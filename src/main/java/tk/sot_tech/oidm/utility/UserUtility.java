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

import Thor.API.Exceptions.tcAPIException;
import Thor.API.Exceptions.tcColumnNotFoundException;
import Thor.API.Exceptions.tcStaleDataUpdateException;
import Thor.API.Exceptions.tcUserNotFoundException;
import Thor.API.Operations.tcUserOperationsIntf;
import Thor.API.tcResultSet;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;
import oracle.iam.identity.exception.NoSuchUserException;
import oracle.iam.identity.exception.UserDisableException;
import oracle.iam.identity.exception.ValidationFailedException;
import oracle.iam.identity.usermgmt.api.UserManager;
import oracle.iam.identity.usermgmt.vo.User;
import oracle.iam.passwordmgmt.api.PasswordMgmtService;
import oracle.iam.passwordmgmt.utils.Utils;
import oracle.iam.passwordmgmt.vo.OimPasswordPolicy;
import oracle.iam.passwordmgmt.vo.PasswordPolicyInfo;
import oracle.iam.platform.authz.exception.AccessDeniedException;
import oracle.iam.selfservice.self.selfmgmt.api.AuthenticatedSelfService;
import oracle.idm.common.ipf.api.password.RandomPasswordGenerator;
import oracle.idm.common.ipf.api.password.RandomPasswordGeneratorImpl;
import static tk.sot_tech.oidm.utility.Misc.OPERATION_SUCCESS_VALUE;
import static tk.sot_tech.oidm.utility.Misc.isNullOrEmpty;

public class UserUtility extends ServiceProvider<tcUserOperationsIntf> {

	private final UserManager newService = Platform.getService(UserManager.class);
	private static final Logger LOG = Logger.getLogger(UserUtility.class.getName());
	public static final String USR_KEY_IN_OIM = "Users.Key",
			USR_LOGIN_IN_OIM = "Users.User ID",
			USR_DISPLAY_NAME_IN_OIM = "Users.Display Name",
			USR_MANAGER_IN_OIM = "Users.Manager Key",
			USR_STATUS_IN_OIM = "Users.Status";
	public static final char MULTI_ATTRIBUTE_SEPARATOR = ';';

	/**
	 * Получение новой платформы пользователей (UserManager)
	 *
	 * @return
	 */
	public UserManager getNewService() {
		return newService;
	}

	/**
	 * Получение атрибута с формы пользователя
	 *
	 * @param userKey   ключ пользователя
	 * @param fieldName имя атрибута
	 *
	 * @return значение атрибута
	 *
	 * @throws tcAPIException
	 * @throws tcColumnNotFoundException
	 */
	public String getUserAttribute(long userKey, String fieldName) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> hashMap = new HashMap<>(1);
		hashMap.put(USR_KEY_IN_OIM, userKey);
		tcResultSet rs = service.findUsersFiltered(hashMap, new String[]{fieldName});
		if (isNullOrEmpty(rs)) {
			return null;
		}
		rs.goToRow(0);
		return rs.getStringValue(fieldName);
	}

	/**
	 *
	 * Получение атрибута с формы пользователя
	 *
	 * @param userLogin логин пользователя
	 * @param fieldName имя атрибута
	 *
	 * @return значение атрибута
	 *
	 * @throws tcAPIException
	 * @throws tcColumnNotFoundException
	 */
	public String getUserAttribute(String userLogin, String fieldName) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> hashMap = new HashMap<>(1);
		hashMap.put(USR_LOGIN_IN_OIM, userLogin);
		tcResultSet rs = service.findUsersFiltered(hashMap, new String[]{fieldName});
		if (isNullOrEmpty(rs)) {
			return null;
		}
		rs.goToRow(0);
		return rs.getStringValue(fieldName);
	}

	/**
	 * Установка атрибута на форму пользователя.
	 * ИСПОЛЬЗУЕТСЯ НОВАЯ ПЛАТФОРМА, необходимо правильно указывать названия поля.
	 *
	 * @param userId     ключ пользователя
	 * @param fieldName  название поля
	 * @param fieldValue значение поля
	 *
	 * @return статус действия или сообщение об ошибке.
	 *
	 * @throws Thor.API.Exceptions.tcColumnNotFoundException
	 */
	public String setUserFormField(long userId, String fieldName, String fieldValue) throws tcColumnNotFoundException {
		HashMap<String, Object> hashMap = new HashMap<>(1);
		hashMap.put(USR_KEY_IN_OIM, userId);
		try {
			tcResultSet rs = service.findUsersFiltered(hashMap, new String[]{fieldName});
			if (isNullOrEmpty(rs)) {
				return null;
			}
			rs.goToRow(0);
			String oldValue = rs.getStringValue(fieldName);
			if (oldValue == null) {
				oldValue = "";
			}
			hashMap.clear();
			if (!oldValue.equalsIgnoreCase(fieldValue)) {
				hashMap.put(fieldName, fieldValue);
				service.updateUser(rs, hashMap);
			}
		}
		catch (tcAPIException | tcUserNotFoundException | tcStaleDataUpdateException ex) {
			LOG.severe(Misc.ownStack(ex));
			return ex.toString();
		}
		return OPERATION_SUCCESS_VALUE;
	}

	public String appendUserFormField(long userId, String fieldName, String fieldValue) {

		HashMap<String, Object> hashMap = new HashMap<>(1);
		hashMap.put(USR_KEY_IN_OIM, userId);
		try {
			tcResultSet rs = service.findUsersFiltered(hashMap, new String[]{fieldName});
			if (isNullOrEmpty(rs)) {
				return null;
			}
			rs.goToRow(0);
			String field = rs.getStringValue(fieldName);
			hashMap.clear();

			if (isNullOrEmpty(field)) {
				hashMap.put(fieldName, fieldValue);
			}
			else if (!field.contains(fieldValue)) {
				hashMap.put(fieldName, fieldValue + MULTI_ATTRIBUTE_SEPARATOR);
			}
			service.updateUser(rs, hashMap);
		}
		catch (tcAPIException | tcColumnNotFoundException | tcUserNotFoundException | tcStaleDataUpdateException ex) {
			LOG.severe(Misc.ownStack(ex));
			return ex.toString();
		}
		return OPERATION_SUCCESS_VALUE;
	}

	public String removeUserFormField(long userId, String fieldName, String fieldValue) {

		HashMap<String, Object> hashMap = new HashMap<>(1);
		hashMap.put(USR_KEY_IN_OIM, userId);
		try {
			tcResultSet rs = service.findUsersFiltered(hashMap, new String[]{fieldName});
			if (isNullOrEmpty(rs)) {
				return null;
			}
			rs.goToRow(0);
			String field = rs.getStringValue(fieldName);
			hashMap.clear();

			if (!isNullOrEmpty(field)) {
				field = field.replace(fieldValue, "").replace(";;", ";");
				if (("" + MULTI_ATTRIBUTE_SEPARATOR).equalsIgnoreCase(field)) {
					field = "";
				}
				hashMap.put(fieldName, field);
				service.updateUser(rs, hashMap);
			}
		}
		catch (tcAPIException | tcColumnNotFoundException | tcUserNotFoundException | tcStaleDataUpdateException ex) {
			LOG.severe(Misc.ownStack(ex));
			return ex.toString();
		}
		return OPERATION_SUCCESS_VALUE;
	}

	public User getCurrentUserInfo(Set<String> attributes) {
		AuthenticatedSelfService authService = Platform.getService(AuthenticatedSelfService.class);
		User u = null;
		try {
			u = authService.getProfileDetails(attributes);
		}
		catch (oracle.iam.selfservice.exception.UserLookupException ex) {
			LOG.severe(Misc.ownStack(ex));
		}
		return u;
	}

	public String lockUser(long uid) {
		try {
			return newService.disable(String.valueOf(uid), false).getStatus();
		}
		catch (ValidationFailedException | AccessDeniedException |
			   UserDisableException | NoSuchUserException ex) {
			LOG.severe(Misc.ownStack(ex));
			return ex.toString();
		}
	}

	public char[] generatePasswordForUser(String login) {
		PasswordMgmtService pwdService = Platform.getService(PasswordMgmtService.class);
		PasswordPolicyInfo passwordPolicyInfo = pwdService.getApplicablePasswordPolicy(login, true);
		return generatePasswordFromPolicy(passwordPolicyInfo);
	}

	public char[] generatePasswordFromPolicy(String policyName) {
		PasswordMgmtService pwdService = Platform.getService(PasswordMgmtService.class);
		PasswordPolicyInfo passwordPolicyInfo = pwdService.getDetails(policyName);
		return generatePasswordFromPolicy(passwordPolicyInfo);
	}

	public char[] generatePasswordFromPolicy(PasswordPolicyInfo passwordPolicyInfo) {
		RandomPasswordGenerator randomPasswordGenerator = new RandomPasswordGeneratorImpl();
		OimPasswordPolicy policy = new OimPasswordPolicy(Utils.getIpfPasswordPolicyInfoVO(passwordPolicyInfo));
		policy.setId(passwordPolicyInfo.getId());
		policy.setName(passwordPolicyInfo.getName());
		char[] ca = randomPasswordGenerator.generatePassword(policy, null);
		return ca;
	}

	@Override
	protected Class<? extends tcUserOperationsIntf> getServiceClass() {
		return tcUserOperationsIntf.class;
	}

}
