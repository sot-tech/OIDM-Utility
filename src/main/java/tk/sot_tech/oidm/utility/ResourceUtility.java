/*
 * Copyright (c) 2016, eramde
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package tk.sot_tech.oidm.utility;

import Thor.API.Exceptions.*;
import Thor.API.Operations.tcFormDefinitionOperationsIntf;
import Thor.API.Operations.tcFormInstanceOperationsIntf;
import Thor.API.Operations.tcObjectOperationsIntf;
import Thor.API.tcResultSet;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import oracle.iam.api.OIMService;
import oracle.iam.exception.OIMServiceException;
import oracle.iam.platform.authopss.exception.AccessDeniedException;
import oracle.iam.platform.entitymgr.vo.SearchCriteria;
import oracle.iam.platform.utils.vo.OIMType;
import oracle.iam.provisioning.api.ApplicationInstanceService;
import oracle.iam.provisioning.api.ProvisioningService;
import oracle.iam.provisioning.exception.AccountNotFoundException;
import oracle.iam.provisioning.exception.GenericAppInstanceServiceException;
import oracle.iam.provisioning.exception.GenericProvisioningException;
import oracle.iam.provisioning.exception.UserNotFoundException;
import oracle.iam.provisioning.vo.Account;
import static oracle.iam.provisioning.vo.Account.ACCOUNT_TYPE.Primary;
import oracle.iam.provisioning.vo.AccountData;
import oracle.iam.provisioning.vo.ApplicationInstance;
import oracle.iam.provisioning.vo.ChildTableRecord;
import oracle.iam.request.vo.*;
import oracle.iam.vo.OperationResult;
import static tk.sot_tech.oidm.utility.Misc.OPERATION_SUCCESS_VALUE;
import static tk.sot_tech.oidm.utility.Misc.isNullOrEmpty;
import static tk.sot_tech.oidm.utility.Misc.ownStack;
import static tk.sot_tech.oidm.utility.UserUtility.USR_KEY_IN_OIM;

public class ResourceUtility extends ServiceProvider {

	private final tcObjectOperationsIntf objectService = Platform.getService(
		tcObjectOperationsIntf.class);
	private final tcFormDefinitionOperationsIntf formService = Platform.getService(
		tcFormDefinitionOperationsIntf.class);
	private final ProvisioningService provisioningService = Platform.getService(
		ProvisioningService.class);
	private final tcFormInstanceOperationsIntf formInstanceService = Platform.getService(
		tcFormInstanceOperationsIntf.class);

	public static final String[] FORM_IGNORE_FIELDS = {"_KEY", "_CREATE", "_ROWVER", "_UPDATE",
													   "_CREATEBY",
													   "_REVOKE", "_NOTE", "_UPDATEBY", "_VERSION",
													   "_DATA_LEVEL"};

	private static final Logger LOG = Logger.getLogger(ResourceUtility.class.getName());

	public tcObjectOperationsIntf getObjectService() {
		return objectService;
	}

	public tcFormDefinitionOperationsIntf getFormService() {
		return formService;
	}

	public ProvisioningService getProvisioningService() {
		return provisioningService;
	}

	public tcFormInstanceOperationsIntf getFormInstanceService() {
		return formInstanceService;
	}

	public long getFormKey(String name) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> search = new HashMap<>();
		search.put("Structure Utility.Table Name", name);
		tcResultSet found = formService.findForms(search);
		found.goToRow(0);
		return found.getLongValue("Structure Utility.Key");

	}
	
	public String getFormName(long key) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> search = new HashMap<>();
		search.put("Structure Utility.Key", key);
		tcResultSet found = formService.findForms(search);
		found.goToRow(0);
		return found.getStringValue("Structure Utility.Table Name");

	}

	public long getObjectKey(String name) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> search = new HashMap<>();
		search.put("Objects.Name", name);
		tcResultSet found = objectService.findObjects(search);
		found.goToRow(0);
		return found.getLongValue("Objects.Key");
	}
	
	public String getObjectName(long key) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> search = new HashMap<>();
		search.put("Objects.Key", key);
		tcResultSet found = objectService.findObjects(search);
		found.goToRow(0);
		return found.getStringValue("Objects.Name");
	}

	public Map<String, Object> getFormFieldsForRender(String formName, Map<String, Object> data)
		throws tcAPIException, tcColumnNotFoundException, tcFormNotFoundException {
		for (String s : FORM_IGNORE_FIELDS) {
			data.remove(formName + s);
		}
		long formKey = getFormKey(formName);
		tcResultSet rs = formService.getFormVersions(formKey);
		if (!isNullOrEmpty(rs)) {
			rs.goToRow(0);
			int lastVer = rs.getIntValue("Structure Utility.Active Version");
			rs = formService.getFormFields(formKey, lastVer);
			for (int i = 0; i < rs.getRowCount(); ++i) {
				rs.goToRow(i);
				String field = rs.getStringValue("Structure Utility.Additional Columns.Name"),
					label = rs.getStringValue("Structure Utility.Additional Columns.Field Label");
				if ("1".equalsIgnoreCase(rs.getStringValue(
					"Structure Utility.Additional Columns.Encrypted"))
					|| "false".equalsIgnoreCase(rs.getStringValue("VISIBLE"))) {
					data.remove(field);
				} else if (!isNullOrEmpty(label)) {
					data.put(label, data.remove(field));
				}
			}
		}
		return data;
	}

	public String getFormItResourceFieldName(String formName) throws tcAPIException,
																	 tcFormNotFoundException,
																	 tcColumnNotFoundException {
		ArrayList<String> formFields = getFormFieldNamesByType(formName, "ITResourceLookupField");
		return formFields.isEmpty()?null:formFields.get(0);
	}
	
	public HashMap<String, String> getFormFieldNamesAndTypes(String formName) throws tcAPIException,
																	 tcFormNotFoundException,
																	 tcColumnNotFoundException {
		HashMap<String, String> res = new HashMap<>();
		long formKey = getFormKey(formName);
		tcResultSet rs = formService.getFormVersions(formKey);
		if (!isNullOrEmpty(rs)) {
			rs.goToRow(0);
			int lastVer = rs.getIntValue("Structure Utility.Active Version");
			rs = formService.getFormFields(formKey, lastVer);
			for (int i = 0; i < rs.getRowCount(); ++i) {
				rs.goToRow(i);
				String field = rs.getStringValue("Structure Utility.Additional Columns.Name"),
					type = rs.getStringValue("Structure Utility.Additional Columns.Field Type");
				res.put(field, type);
			}
		}
		return res;
	}
	
	public ArrayList<String> getFormFieldNamesByType(String formName, String type) throws tcAPIException,
																	 tcFormNotFoundException,
																	 tcColumnNotFoundException{
		ArrayList<String> res = new ArrayList<>();
		for(Entry<String, String> e : getFormFieldNamesAndTypes(formName).entrySet()){
			if(type.equalsIgnoreCase(e.getValue())){
				res.add(e.getKey());
			}
		}
		return res;
	}

	public String getAppInstanceName(long objectId, long itResource) throws
		GenericAppInstanceServiceException {
		ApplicationInstanceService aiService = Platform.getService(ApplicationInstanceService.class);

		SearchCriteria objectCriteria = new SearchCriteria(ApplicationInstance.OBJ_KEY, String.
														   valueOf(objectId).replaceAll("\\.\\, ",
																						""),
														   SearchCriteria.Operator.EQUAL),
			itCriteria = new SearchCriteria(ApplicationInstance.ITRES_KEY, String.
											valueOf(itResource).replaceAll("\\.\\, ", ""),
											SearchCriteria.Operator.EQUAL);

		List<ApplicationInstance> aiList = aiService.findApplicationInstance(new SearchCriteria(
			objectCriteria,
			itCriteria,
			SearchCriteria.Operator.AND),
																			 new HashMap<String, Object>());
		if (!isNullOrEmpty(aiList)) {
			return aiList.get(0).getApplicationInstanceName();
		}
		return null;
	}

	public String getFormDescription(String name) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> search = new HashMap<>();
		search.put("Structure Utility.Table Name", name);
		tcResultSet found = formService.findForms(search);
		found.goToRow(0);
		return found.getStringValue("Structure Utility.Description");
	}

	public String renderFormValues(Map<String, Object> data) throws tcAPIException,
																	tcColumnNotFoundException,
																	tcFormNotFoundException {
		StringBuilder sb = new StringBuilder();
		getFormFieldsForRender(data.keySet().iterator().next().replaceAll("(UD_[A-Z0-9]*).*", "$1"),
							   data);
		for (String key : data.keySet()) {
			sb.append(key)
				.append(": ")
				.append(data.get(key))
				.append('\n');
		}
		return sb.toString();
	}

	public String renderChildFormsValues(Map<String, ArrayList<ChildTableRecord>> childData) throws
		tcAPIException, tcColumnNotFoundException, tcFormNotFoundException {
		StringBuilder sb = new StringBuilder();
		for (String childName : childData.keySet()) {
			sb.append(getFormDescription(childName));
			sb.append(":\n");
			for (ChildTableRecord ctr : childData.get(childName)) {
				Map<String, Object> data = ctr.getChildData();
				getFormFieldsForRender(childName, data);
				for (String field : data.keySet()) {
					sb.append(ctr.getAction())
						.append(" - ")
						.append(field)
						.append(": ")
						.append(data.get(field))
						.append('\n');
				}
			}
		}
		return sb.toString();
	}

	public Account getAccountByProcessInstanceKey(long userId, long processInstanceKey) {
		try {
			List<Account> accounts = provisioningService.getAccountsProvisionedToUser(String.
				valueOf(userId), true);
			String procInstKey = String.valueOf(processInstanceKey);
			for (Account a : accounts) {
				if (procInstKey.equals(a.getProcessInstanceKey())) {
					return a;
				}
			}
		} catch (Exception ex) {
			LOG.severe(Misc.ownStack(ex));
		}
		return null;
	}

//	private static final String GET_ACCOUNT_ID_BY_ORC = "SELECT OIU_KEY FROM OIU WHERE ORC_KEY = ?";
//	
//	public Account getAccountByProcessInstanceKey(long processInstanceKey) {
//		try {
//			ArrayList<HashMap<String, Object>> result = Utility.executeQuery(GET_ACCOUNT_ID_BY_ORC, processInstanceKey);
//			if(!isNullOrEmpty(result)){
//				Long accountKey = ((Number)result.get(0).get("OIU_KEY")).longValue();
//				return provisioningService.getAccountDetails(accountKey);
//			}
//			
//		}
//		catch (Exception ex) {
//			Logger.getLogger(ResourceUtility.class.getName()).severe(Misc.ownStack(ex));
//		}
//		return null;
//	}
//	
//	public boolean isResourcePrimary(long processInstanceKey){
//		Account account = getAccountByProcessInstanceKey(processInstanceKey);
//		return account.getAccountType() == ACCOUNT_TYPE.Primary;
//	}
	public String getPrimaryResourceField(String userLogin, String appInstanceName, String fieldName)
															throws tcAPIException, 
																   tcColumnNotFoundException, 
																	UserNotFoundException, 
																	GenericProvisioningException, 
																	tcNotAtomicProcessException,
																	tcFormNotFoundException, 
																	tcProcessNotFoundException {
		String userId = null;
		try (UserUtility uu = new UserUtility()) {
			userId = uu.getUserAttribute(userLogin, USR_KEY_IN_OIM);
		} catch (Exception ex) {
			LOG.severe(ownStack(ex));
		}
		List<Account> accounts = provisioningService.getAccountsProvisionedToUser(userId);
		for (Account account : accounts) {
			ApplicationInstance appInstance = account.getAppInstance();
			String ain = appInstance.getApplicationInstanceName();
			String status = account.getAccountStatus();
			LOG.log(Level.INFO, "INPUT: {0}, {1}, {2}", new Object[]{ain, status, account.
																	 getAccountType()});
			if ((status.equalsIgnoreCase("Provisioned")
				 || status.equalsIgnoreCase("Enabled") /*
				  * || status.equalsIgnoreCase("Disabled")
				  */)
				&& account.getAccountType() == Primary && ain.equals(appInstanceName)) {
				Long processInstanceKey = Long.decode(account.getProcessInstanceKey());
				tcResultSet formData = formInstanceService.getProcessFormDataInViewMode(
					processInstanceKey);
				if (!isNullOrEmpty(formData)) {
					for (int i = 0; i < formData.getRowCount(); ++i) {
						formData.goToRow(i);
						return formData.getStringValue(fieldName);
					}
				}
			}
		}
		return null;
	}

	public String setResourceField(long processInstanceKey, String fieldName, String fieldValue)
		throws tcAPIException,
			   tcInvalidValueException,
			   tcNotAtomicProcessException,
			   tcFormNotFoundException,
			   tcRequiredDataMissingException,
			   tcProcessNotFoundException {
		HashMap<String, Object> map = new HashMap<>();
		map.put(fieldName, fieldValue);
		return setResourceFields(processInstanceKey, map);
	}

	public String setResourceFields(long processInstanceKey, HashMap<String, Object> fieldValues)
		throws tcAPIException,
			   tcInvalidValueException,
			   tcNotAtomicProcessException,
			   tcFormNotFoundException,
			   tcRequiredDataMissingException,
			   tcProcessNotFoundException {
		formInstanceService.setProcessFormData(processInstanceKey, fieldValues);
		return OPERATION_SUCCESS_VALUE;
	}

	public String setAccountField(long userId, long procInstKey, String field, String value) throws
		AccountNotFoundException, AccessDeniedException, GenericProvisioningException {
		Account acc = getAccountByProcessInstanceKey(userId, procInstKey);
		AccountData accData = acc.getAccountData();
		Map<String, Object> data = accData.getData();
		data.put(field, value);
		provisioningService.modify(acc);
		return OPERATION_SUCCESS_VALUE;
	}

//	public String initUpdateResourceRequest(String userKey, String procInstKey, String field, Serializable value, RequestBeneficiaryEntityAttribute.TYPE type) throws OIMServiceException {
//		OIMService oimService = Platform.getService(OIMService.class);
//		RequestData requestData = new RequestData();
//		Beneficiary beneficiary = new Beneficiary();
//		beneficiary.setBeneficiaryKey(userKey);
//		beneficiary.setBeneficiaryType(Beneficiary.USER_BENEFICIARY);
//
//		List<RequestBeneficiaryEntityAttribute> benEntityParams = new ArrayList<>();
//		RequestBeneficiaryEntityAttribute benEntityParam = new RequestBeneficiaryEntityAttribute();
//		benEntityParam.setName(field);
//		benEntityParam.setType(type);
//		benEntityParam.setValue(value);
//		benEntityParam.setAction(RequestBeneficiaryEntityAttribute.ACTION.Modify);
//		benEntityParam.setHasChild(false);
//		benEntityParams.add(benEntityParam);
//
//		ResObjUtil rou = new ResObjUtil();
//		Account account = rou.getAccountByProcessInstanceKey(Long.decode(procInstKey));
//		String appInstanceName = account.getAppInstance().getApplicationInstanceName();
//		RequestBeneficiaryEntity requestEntity = new RequestBeneficiaryEntity();
//		requestEntity.setRequestEntityType(OIMType.ApplicationInstance);
//		requestEntity.setEntitySubType(appInstanceName);
//		requestEntity.setEntityKey(account.getAccountID());
//		requestEntity.setOperation("MODIFY");
//		requestEntity.setEntityData(benEntityParams);
//
//		List<RequestBeneficiaryEntity> entities = new ArrayList<>();
//		entities.add(requestEntity);
//
//		beneficiary.setTargetEntities(entities);
//
//		List<Beneficiary> beneficiaries = new ArrayList<>();
//		beneficiaries.add(beneficiary);
//		requestData.setBeneficiaries(beneficiaries);
//
//		OperationResult operationResult = oimService.doOperation(requestData,
//																 OIMService.Intent.REQUEST);
//
//		if (operationResult == null) {
//			return null;
//		}
//		return operationResult.getOperationStatus().name();
//	}
//
//	public String initUpdateResourceRequest(String userKey, String procInstKey, String field, String value) throws OIMServiceException {
//		return initUpdateResourceRequest(userKey, procInstKey, field, value, RequestBeneficiaryEntityAttribute.TYPE.String);
//	}
//
//	public String initUpdateResourceRequest(String userKey, String procInstKey, String field, Date value) throws OIMServiceException {
//		return initUpdateResourceRequest(userKey, procInstKey, field, value, RequestBeneficiaryEntityAttribute.TYPE.Date);
//	}

	public String initCreateResourceRequest(String userKey, String applicationInstanceName,
											HashMap<String, Serializable> fields, boolean eh) throws tcAPIException, tcColumnNotFoundException,
																									 tcFormNotFoundException, OIMServiceException {
		ApplicationInstanceService ais = eh
										 ? oracle.iam.platform.Platform.getServiceForEventHandlers(ApplicationInstanceService.class,
																			   null, null, null, null)
										 : Platform.getService(ApplicationInstanceService.class);
		ApplicationInstance applicationInstance = ais.findApplicationInstanceByName(applicationInstanceName);
		OIMService oimService = eh
								? oracle.iam.platform.Platform.getServiceForEventHandlers(OIMService.class,
																	  null, null, null, null)
								: Platform.getService(OIMService.class);
		RequestData requestData = new RequestData();
		Beneficiary beneficiary = new Beneficiary();
		beneficiary.setBeneficiaryKey(userKey);
		beneficiary.setBeneficiaryType(Beneficiary.USER_BENEFICIARY);

		List<RequestBeneficiaryEntityAttribute> benEntityParams = new ArrayList<>();
		RequestBeneficiaryEntity requestEntity = new RequestBeneficiaryEntity();
		requestEntity.setRequestEntityType(OIMType.ApplicationInstance);
		requestEntity.setEntitySubType(applicationInstanceName);
		requestEntity.setEntityKey(String.valueOf(applicationInstance.getApplicationInstanceKey()).replaceAll(",\\. ", ""));
		requestEntity.setOperation(RequestConstants.MODEL_PROVISION_APPLICATION_INSTANCE_OPERATION);
		for (Map.Entry<String, Serializable> field : fields.entrySet()) {
			Serializable value = field.getValue();
			RequestBeneficiaryEntityAttribute.TYPE type;
			if (value != null) {
				if (value instanceof Integer) {
					type = RequestBeneficiaryEntityAttribute.TYPE.Integer;
				}
				else if (value instanceof Long) {
					type = RequestBeneficiaryEntityAttribute.TYPE.Long;
				}
				else if (value instanceof Date) {
					type = RequestBeneficiaryEntityAttribute.TYPE.Date;
				}
				else if (value instanceof Boolean) {
					type = RequestBeneficiaryEntityAttribute.TYPE.Boolean;
				}
				else {
					type = RequestBeneficiaryEntityAttribute.TYPE.String;
					value = String.valueOf(value);
				}
				LOG.log(Level.INFO, "{0} TYPE: {1}", new Object[]{field, value.getClass().getSimpleName()});
				RequestBeneficiaryEntityAttribute benEntityParam = new RequestBeneficiaryEntityAttribute(field.getKey(), value, type);
				benEntityParams.add(benEntityParam);
			}

		}
		requestEntity.setEntityData(benEntityParams);

		List<RequestBeneficiaryEntity> entities = new ArrayList<>();
		entities.add(requestEntity);

		beneficiary.setTargetEntities(entities);

		List<Beneficiary> beneficiaries = new ArrayList<>();
		beneficiaries.add(beneficiary);
		requestData.setBeneficiaries(beneficiaries);

		OperationResult operationResult = oimService.doOperation(requestData,
																 OIMService.Intent.REQUEST);
		if (operationResult == null) {
			return null;
		}
		String result = operationResult.getOperationStatus().name();
		LOG.log(Level.INFO, "AppInstance {0} for userid {1} request {2}", new Object[]{applicationInstanceName, userKey, result});
		return result;
	}

	public String initCreateResourceRequest(String userKey, String applicationInstanceName) throws tcAPIException, tcColumnNotFoundException,
																								   tcFormNotFoundException, OIMServiceException {
		return initCreateResourceRequest(userKey, applicationInstanceName, new HashMap<String, Serializable>(), false);
	}

	public String initRevokeResourceRequest(String userKey, String applicationInstanceName, String accountId, boolean eh) throws OIMServiceException {
		OIMService oimService = eh
								? oracle.iam.platform.Platform.getServiceForEventHandlers(OIMService.class,
																	  null, null, null, null)
								: Platform.getService(OIMService.class);
		RequestData requestData = new RequestData();
		Beneficiary beneficiary = new Beneficiary();
		beneficiary.setBeneficiaryKey(userKey);
		beneficiary.setBeneficiaryType(Beneficiary.USER_BENEFICIARY);
		RequestBeneficiaryEntity requestEntity = new RequestBeneficiaryEntity();
		requestEntity.setRequestEntityType(OIMType.ApplicationInstance);
		requestEntity.setEntitySubType(applicationInstanceName);
		requestEntity.setEntityKey(accountId);
		requestEntity.setOperation(RequestConstants.MODEL_REVOKE_ACCOUNT_OPERATION);

		List<RequestBeneficiaryEntity> entities = new ArrayList<>();
		entities.add(requestEntity);

		beneficiary.setTargetEntities(entities);

		List<Beneficiary> beneficiaries = new ArrayList<>();
		beneficiaries.add(beneficiary);
		requestData.setBeneficiaries(beneficiaries);

		OperationResult operationResult = oimService.doOperation(requestData,
																 OIMService.Intent.REQUEST);

		if (operationResult == null) {
			return null;
		}
		return operationResult.getOperationStatus().name();
	}

	@Override
	protected Class getServiceClass() {
		return null;
	}

	@Override
	protected Object initService() {
		return null;
	}

	@Override
	public void close() {
		objectService.close();
		formService.close();
		formInstanceService.close();
	}

}
