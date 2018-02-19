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
import Thor.API.Operations.tcAccessPolicyOperationsIntf;
import Thor.API.tcResultSet;
import com.thortech.xl.vo.AccessPolicyResourceData;
import com.thortech.xl.vo.PolicyChildTableRecord;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import oracle.iam.identity.exception.*;
import oracle.iam.platform.authz.exception.AccessDeniedException;
import static tk.sot_tech.oidm.utility.Misc.isNullOrEmpty;
import static tk.sot_tech.oidm.utility.RoleUtility.ROLE_KEY_IN_OIM;

public class PolicyUtility extends ServiceProvider<tcAccessPolicyOperationsIntf> {

	private static final Logger LOG = Logger.getLogger(PolicyUtility.class.getName());

	public void clearChildRecordsInTable(AccessPolicyResourceData data, String tableName) throws tcAPIException, tcColumnNotFoundException {
		String formKey;
		try (ResourceUtility resObjUtil = new ResourceUtility()) {
			formKey = String.valueOf(resObjUtil.getFormKey(tableName));
		}
		PolicyChildTableRecord[] childTableRecords = data.getChildTableRecords(formKey);
		if (childTableRecords != null) {
			for (PolicyChildTableRecord record : childTableRecords) {
				data.removeChildTableRecord(formKey, record);
			}
		}
	}

	public long createOwnPolicy(long roleId, long objectKey, long parentFormKey, long itKey, String policyName,
								String policyDescription) throws tcObjectNotFoundException,
														tcGroupNotFoundException,
														tcInvalidAttributeException,
														tcObjectNotAssignedException,
														tcAPIException,
														tcColumnNotFoundException,
														tcFormNotFoundException {
		HashMap attr = new HashMap();
		String formName, itResourceField, objectName;
		try (ResourceUtility ru = new ResourceUtility()) {
			formName = ru.getFormName(parentFormKey);
			itResourceField = ru.getFormItResourceFieldName(formName);
			objectName = ru.getObjectName(objectKey);
		}
		attr.put("Access Policies.Name", policyName); // Policy Name		
		attr.put("Access Policies.Description", policyDescription); // Description same as Policy Name
		attr.put("Access Policies.Retrofit Flag", "1"); // Retrofit Flag
		attr.put("Access Policies.By Request", "0"); // Without Approval
		long[] provObjKeys = {objectKey}; //Object Key of Resource to be provisioned
		boolean[] revokeObjIsNotApply = {true}; //Revoke If No Longer Applies Flag
		long[] denyObjKeys = {}; //Object key of Resource to be denied
		long[] groupKeys = {roleId};

		HashMap formData = new HashMap();
		formData.put(itResourceField, String.valueOf(itKey));
		AccessPolicyResourceData[] data = {new AccessPolicyResourceData(objectKey, objectName, parentFormKey, formName, "P")};
		data[0].setFormData(formData);
		return service.createAccessPolicy(attr, provObjKeys, revokeObjIsNotApply, denyObjKeys, groupKeys, data);
	}

	public String getPolicyName(long key) throws tcAPIException, tcColumnNotFoundException {
		HashMap search = new HashMap();
		search.put("Access Policies.Key", key);
		tcResultSet found = service.findAccessPolicies(search);
		if (!isNullOrEmpty(found)) {
			found.goToRow(0);
			return found.getStringValue("Access Policies.Name");
		}
		return null;
	}

	public long findOrCreatePolicyAndSetRole(String polName, String polDescription, long roleId, long objectKey, long parentFormKey,
											 long itResourceKey) throws AccessDeniedException,
																		NoSuchRoleException,
																		RoleLookupException,
																		ValidationFailedException,
																		RoleAlreadyExistsException,
																		RoleCreateException,
																		tcAPIException,
																		Exception {
		LOG.log(Level.INFO, "Creating/updating policy {0}", polName);
		HashMap<String, Object> search = new HashMap<>();
		search.put("Access Policies.Name", polName);
		tcResultSet found = service.findAccessPolicies(search);
		long ownPolicyId;
		if (isNullOrEmpty(found)) {
			ownPolicyId = createOwnPolicy(roleId, objectKey, parentFormKey, itResourceKey,
										  polName,
										  polDescription);
		} else {
			found.goToRow(0);
			ownPolicyId = found.getLongValue("Access Policies.Key");
		}
		tcResultSet assignedGroups = service.getAssignedGroups(ownPolicyId);
		if (assignedGroups == null || assignedGroups.isEmpty()) {
			service.assignGroups(ownPolicyId, new long[]{roleId});
		} else {
			boolean exist = false;
			for (int i = 0; i < assignedGroups.getRowCount(); ++i) {
				assignedGroups.goToRow(i);
				long currentRoleId = assignedGroups.getLongValue(ROLE_KEY_IN_OIM);
				if (currentRoleId == roleId) {
					exist = true;
					break;
				}
			}
			if (!exist) {
				service.assignGroups(ownPolicyId, new long[]{roleId});
			}
		}
		LOG.log(Level.INFO, "Policy {0} created/updated", polName);
		return ownPolicyId;
	}

	@Override
	protected Class<? extends tcAccessPolicyOperationsIntf> getServiceClass() {
		return tcAccessPolicyOperationsIntf.class;
	}

}
