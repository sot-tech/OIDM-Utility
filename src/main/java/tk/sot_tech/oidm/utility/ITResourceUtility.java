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
import Thor.API.Exceptions.tcITResourceNotFoundException;
import Thor.API.Operations.tcITResourceInstanceOperationsIntf;
import Thor.API.tcResultSet;
import java.util.HashMap;

public class ITResourceUtility extends ServiceProvider<tcITResourceInstanceOperationsIntf> {
	
	public static final String 
			IT_KEY_IN_OIM = "IT Resource.Key",
			IT_NAME_IN_OIM = "IT Resource.Name",
			IT_PARAM_NAME_IN_OIM = "IT Resources Type Parameter.Name",
			IT_PARAM_VALUE_IN_OIM = "IT Resource.Parameter.Value";

	public HashMap<String, String> getITResourceParameters(String itResourceName) throws tcAPIException,
																						 tcITResourceNotFoundException,
																						 tcColumnNotFoundException {

		return getITResourceParameters(getITResourceKey(itResourceName));
	}

	public HashMap<String, String> getITResourceParameters(long itResourceKey) throws tcAPIException,
																					  tcITResourceNotFoundException,
																					  tcColumnNotFoundException {

		HashMap<String, String> params = new HashMap<>();
		tcResultSet its = service.getITResourceInstanceParameters(itResourceKey);
		for (int i = 0; i < its.getRowCount(); ++i) {
			its.goToRow(i);
			params.put(its.getStringValue(IT_PARAM_NAME_IN_OIM),
					   its.getStringValue(IT_PARAM_VALUE_IN_OIM));
		}
		return params;
	}

	public long getITResourceKey(String name) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> hm = new HashMap<>();
		hm.put(IT_NAME_IN_OIM, name);
		tcResultSet its = service.findITResourceInstances(hm);
		if (its == null || its.isEmpty()) {
			throw new IllegalArgumentException("IT Resource " + name + " not exist");
		}
		else if (its.getRowCount() > 1) {
			throw new IllegalArgumentException("IT Resource " + name + " not unique");
		}
		its.goToRow(0);
		return its.getLongValue(IT_KEY_IN_OIM);
	}
	
	public String getITResourceName(long key) throws tcAPIException, tcColumnNotFoundException {
		HashMap<String, Object> hm = new HashMap<>();
		hm.put(IT_KEY_IN_OIM, key);
		tcResultSet its = service.findITResourceInstances(hm);
		if (Misc.isNullOrEmpty(its)) {
			throw new IllegalArgumentException("IT Resource with key " + key + " not exist");
		}
		its.goToRow(0);
		return its.getStringValue(IT_NAME_IN_OIM);
	}

	@Override
	protected Class<? extends tcITResourceInstanceOperationsIntf> getServiceClass() {
		return tcITResourceInstanceOperationsIntf.class;
	}

}
