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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import oracle.iam.catalog.vo.Catalog;
import oracle.iam.catalog.vo.MetaData;
import oracle.iam.catalog.vo.MetaDataDefinition;
import oracle.iam.identity.exception.*;
import oracle.iam.identity.rolemgmt.api.RoleManager;
import oracle.iam.identity.rolemgmt.api.RoleManagerConstants;
import oracle.iam.identity.rolemgmt.vo.Role;
import oracle.iam.identity.rolemgmt.vo.RoleManagerResult;
import oracle.iam.platform.authopss.api.PolicyConstants;
import oracle.iam.platform.authopss.vo.EntityPublication;
import oracle.iam.platform.authz.exception.AccessDeniedException;
import oracle.iam.platform.entitymgr.vo.SearchCriteria;
import oracle.iam.platformservice.api.EntityPublicationService;
import static tk.sot_tech.oidm.utility.Misc.dbFieldToApiField;
import static tk.sot_tech.oidm.utility.Misc.nullToEmpty;
import static tk.sot_tech.oidm.utility.Misc.ownStack;
import static tk.sot_tech.oidm.utility.UserUtility.USR_LOGIN_IN_OIM;

public class RoleUtility extends ServiceProvider<RoleManager> {
	
	public static final String ROLE_KEY_IN_OIM = "Groups.Key",
			ROLE_NAME_IN_OIM = "Groups.Group Name";

	private static final Logger LOG = Logger.getLogger(RoleUtility.class.getName());

	public String getRoleName(String roleId) throws AccessDeniedException, NoSuchRoleException,
													RoleLookupException {
		HashSet<String> search = new HashSet<>();
		search.add(RoleManagerConstants.ROLE_NAME);
		return service.getDetails(roleId, search).getName();
	}

	public String getRoleField(String roleId, String fieldName) throws AccessDeniedException,
																	   NoSuchRoleException,
																	   RoleLookupException {
		HashSet<String> set = new HashSet<>(1);
		set.add(dbFieldToApiField(fieldName));

		return (String) service.getDetails(roleId, set).getAttribute(dbFieldToApiField(fieldName));
	}

	public long findOrCreateRole(String roleName) throws AccessDeniedException,
														 ValidationFailedException,
														 RoleAlreadyExistsException,
														 RoleCreateException,
														 SearchKeyNotUniqueException,
														 NoSuchRoleException,
														 RoleLookupException {
		HashSet<String> hs = new HashSet<>();
		Role details = null;
		try {
			details = service.getDetails(RoleManagerConstants.ROLE_NAME, roleName, hs);
		} catch (NoSuchRoleException | RoleLookupException | SearchKeyNotUniqueException | AccessDeniedException ignore) {
		}
		if (details == null) {
			HashMap<String, Object> attrs = new HashMap<>();
			attrs.put(RoleManagerConstants.ROLE_NAME, roleName);
			attrs.put(RoleManagerConstants.ROLE_DESCRIPTION, roleName);
			details = new Role(attrs);
			RoleManagerResult result = service.create(details);
			if (Misc.OPERATION_SUCCESS_VALUE.equalsIgnoreCase(result.getStatus())) {
				details = service.getDetails(RoleManagerConstants.ROLE_NAME, roleName, hs);
			}
		}
		return Long.decode(details.getEntityId());
	}

	public String removeUserFromRole(String userId, String roleName) throws AccessDeniedException,
																			RoleSearchException,
																			ValidationFailedException,
																			RoleGrantRevokeException {
		HashSet<String> set = new HashSet<>();

		List<Role> found = service.search(
			new SearchCriteria(RoleManagerConstants.ROLE_UNIQUE_NAME, roleName,
							   SearchCriteria.Operator.EQUAL),
			set,
			new HashMap<String, Object>());
		String roleId = found.get(0).getEntityId();
		set = new HashSet<>(1);
		set.add(userId);
		return service.revokeRoleGrant(roleId, set).getStatus();
	}

	public boolean isUserInRoleOrganizations(String userLogin, String roleId) throws
		OrganizationManagerException,
		tcAPIException,
		tcColumnNotFoundException {

		EntityPublicationService eps = Platform.getService(EntityPublicationService.class);
		List<EntityPublication> publishedOrgs = eps.listEntityPublications(
			PolicyConstants.Resources.ROLE,
			roleId, new HashMap());
		ArrayList<String> orgs = new ArrayList<>();
		if (!Misc.isNullOrEmpty(publishedOrgs)) {
			try (OrganizationUtility ou = new OrganizationUtility()) {
				for (EntityPublication ep : publishedOrgs) {
					String orgId = ep.getScopeId();
					if (!orgs.contains(orgId)) {
						orgs.add(orgId);
					}
					if (ep.isHierarchicalScope()) {
						ou.getOrgChildren(orgId, orgs);
					}
				}
				try (UserUtility uu = new UserUtility()) {
					String userId = uu.getUserAttribute(userLogin, USR_LOGIN_IN_OIM);
					for (String orgId : orgs) {
						List<String> orgUsers = ou.getNewService().getOrganizationMemberIds(orgId);
						if (orgUsers.contains(userId)) {
							return true;
						}
					}
				} catch (Exception ex1) {
					LOG.severe(ownStack(ex1));
				}
			} catch (Exception ex0) {
				LOG.severe(ownStack(ex0));
			}
		}
		return false;
	}

	public ArrayList<Long> getPolicies(String roleId) throws AccessDeniedException,
															 NoSuchRoleException,
															 RoleLookupException {
		HashSet<String> set = new HashSet<>(1);
		set.add(RoleManagerConstants.ACCESS_POLICIES);
		ArrayList<Long> result = new ArrayList<>();
		Role details = service.getDetails(roleId, set);
		LOG.log(Level.FINE, "GOT ROLE DETAILS {0}", details);
		List<String> accessPolicies = (List<String>) details.getAttribute(
			RoleManagerConstants.ACCESS_POLICIES);
		LOG.log(Level.FINE, "ROLE POLICIES BEFORE CONVERTION: {0}", accessPolicies);
		if (accessPolicies != null) {
			for (String accessPolicyId : accessPolicies) {
				LOG.log(Level.FINE, "FOUND POLICY {0}", accessPolicyId);
				result.add(Long.decode(accessPolicyId));
			}
		}
		LOG.log(Level.FINE, "ROLE POLICIES: {0}", result);
		return result;
	}

	public boolean isValueTheSame(String roleId, String name, Object expValue) {
		try {
			String roleField = null;
			try {
				roleField = getRoleField(roleId, name);
			} catch (RoleLookupException ex) {
				if (nullToEmpty(ex.getMessage()).contains("IAM-3056008")) {
					Field[] fields = RoleManagerConstants.class.getFields();
					for (Field f : fields) {
						if (name.equalsIgnoreCase(f.getName())) {
							try {
								roleField = (String) f.get(null);
								roleField = getRoleField(roleId, roleField);
							} catch (IllegalArgumentException | IllegalAccessException ex1) {
								Logger.getLogger(RoleUtility.class.getName()).log(Level.SEVERE,
																				  ownStack(ex1));
							}
						}
					}
				} else {
					throw ex;
				}
			}
			return String.valueOf(expValue).equals(roleField);
		} catch (AccessDeniedException | NoSuchRoleException | RoleLookupException ex) {
			Logger.getLogger(RoleUtility.class.getName()).log(Level.SEVERE, ownStack(ex));
		}
		return false;
	}

	public boolean isCatalogValueTheSame(String roleId, String name, Object expValue) {
		try {
			HashSet<String> hs = new HashSet<>();
			hs.add(RoleManagerConstants.CATALOG_ATTRIBUTES);
			Role details = service.getDetails(roleId, hs);
			Catalog catalog = (Catalog) details.getAttribute(
				RoleManagerConstants.CATALOG_ATTRIBUTES);
			for (MetaData m : catalog.getMetadata()) {
				MetaDataDefinition mdf = m.getMetaDataDefinition();
				if (name.equalsIgnoreCase(mdf.getDbColumnName()) || name.equalsIgnoreCase(mdf.
					getDisplayName())) {
					String value = m.getValue().toUpperCase();
					return String.valueOf(expValue).equalsIgnoreCase(value);
				}
			}
		} catch (AccessDeniedException | NoSuchRoleException | RoleLookupException ex) {
			Logger.getLogger(RoleUtility.class.getName()).log(Level.SEVERE, null, ex);
		}
		return false;
	}

	@Override
	protected Class<? extends RoleManager> getServiceClass() {
		return RoleManager.class;
	}

}
