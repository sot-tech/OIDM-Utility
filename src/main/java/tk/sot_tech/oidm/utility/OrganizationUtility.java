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
import Thor.API.Operations.tcOrganizationOperationsIntf;
import Thor.API.tcResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import oracle.iam.identity.exception.OrganizationManagerException;
import oracle.iam.identity.orgmgmt.api.OrganizationManager;
import oracle.iam.identity.orgmgmt.vo.Organization;
import static tk.sot_tech.oidm.utility.Misc.isNullOrEmpty;
import static tk.sot_tech.oidm.utility.UserUtility.USR_KEY_IN_OIM;

public class OrganizationUtility extends ServiceProvider<tcOrganizationOperationsIntf> {

	public static final String ORG_KEY_IN_OIM = "Organizations.Key",
		ORG_NAME_IN_OIM = "Organizations.Organization Name",
		ORG_STATIS_IN_OIM = "Organizations.Status",
		ORG_STATUS_ACTIVE_VALUE_IN_OIM = "Active",
		ORG_PARENT_KEY_IN_OIM = "Organizations.Parent Key";

	private final OrganizationManager newService = Platform.getService(OrganizationManager.class);

	public OrganizationManager getNewService() {
		return newService;
	}

	public void moveOrg(long id, long parentId, boolean checkExistence) throws tcAPIException,
																			   tcOrganizationNotFoundException,
																			   tcBulkException,
																			   tcColumnNotFoundException {

		if (checkExistence) {
			HashMap<String, Object> hm = new HashMap<>();
			hm.put(ORG_KEY_IN_OIM, id);
			tcResultSet parent = service.findOrganizationsFiltered(hm, new String[]{
				ORG_PARENT_KEY_IN_OIM});
			parent.goToRow(0);
			if (parent.getLongValue(ORG_PARENT_KEY_IN_OIM) != parentId) {
				service.moveOrganizations(new long[]{id}, parentId);
			}
		} else {
			service.moveOrganizations(new long[]{id}, parentId);
		}
	}

	public void moveOrg(long id, long parentId) throws tcAPIException,
													   tcOrganizationNotFoundException,
													   tcBulkException, tcColumnNotFoundException {
		moveOrg(id, parentId, true);
	}

	public void moveUser(long id, long orgId) throws tcAPIException, tcColumnNotFoundException,
													 tcUserNotFoundException,
													 tcOrganizationNotFoundException,
													 tcBulkException {
		if (id < 0 || orgId < 0) {
			Logger.getLogger(OrganizationUtility.class.getName()).log(Level.SEVERE,
														  "User ID ({0}) or Parent org ID ({1}) are invalid",
														  new Object[]{id, orgId});
			return;
		}
		try (UserUtility usr = new UserUtility()) {
			HashMap<String, Object> hm = new HashMap<>();
			hm.put(USR_KEY_IN_OIM, id);
			tcResultSet user = usr.getService().findUsersFiltered(hm, new String[]{ORG_KEY_IN_OIM});
			user.goToRow(0);
			if (user.getLongValue(ORG_KEY_IN_OIM) != orgId) {
				service.moveUsers(new long[]{id}, orgId);
			}
		} catch (Exception ex) {
			Logger.getLogger(OrganizationUtility.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public String getOrgName(long key) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, String> hm = new HashMap<>();
		hm.put(ORG_KEY_IN_OIM, String.valueOf(key));
		tcResultSet org = service.findOrganizations(hm);
		if (isNullOrEmpty(org)) {
			return "";
		}
		org.goToRow(0);
		return org.getStringValue(ORG_NAME_IN_OIM);
	}

	public String getOrgAttribute(long orgKey, String fieldName) throws tcAPIException,
																		tcColumnNotFoundException {
		HashMap<String, Object> hashMap = new HashMap<>(1);
		hashMap.put(ORG_KEY_IN_OIM, orgKey);
		tcResultSet rs = service.findOrganizationsFiltered(hashMap, new String[]{fieldName});
		if (isNullOrEmpty(rs)) {
			return null;
		}
		rs.goToRow(0);
		return rs.getStringValue(fieldName);
	}

	public String getOrgAttribute(String orgKey, String fieldName) throws tcAPIException,
																		  tcColumnNotFoundException {
		return getOrgAttribute(Long.decode(orgKey), fieldName);
	}
	
	/**
	 * Better use SQL query...
	 * @param orgId
	 * @return
	 * @throws OrganizationManagerException 
	 */
	public ArrayList<String> getOrgChildren(String orgId) throws OrganizationManagerException{
		ArrayList<String> orgs = new ArrayList<>();
		getOrgChildren(orgId, orgs);
		return orgs;
	}

	protected void getOrgChildren(String orgId, ArrayList<String> orgs) throws OrganizationManagerException {
		HashSet<String> hs = new HashSet<>();
		List<Organization> childs = newService.getChildOrganizations(orgId, hs, new HashMap());
		if (childs != null) {
			for (Organization org : childs) {
				String childId = org.getEntityId();
				if (!orgs.contains(childId)) {
					orgs.add(childId);
				}
				getOrgChildren(childId, orgs);
			}
		}
	}

	@Override
	protected Class<? extends tcOrganizationOperationsIntf> getServiceClass() {
		return tcOrganizationOperationsIntf.class;
	}

}
