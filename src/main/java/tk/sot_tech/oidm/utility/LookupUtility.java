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
import Thor.API.Exceptions.tcInvalidLookupException;
import Thor.API.Operations.tcLookupOperationsIntf;
import Thor.API.tcResultSet;
import java.util.ArrayList;
import java.util.HashMap;

public class LookupUtility extends ServiceProvider<tcLookupOperationsIntf> {
	
	public static final String LK_CODE_IN_OIM = "Lookup Definition.Lookup Code Information.Code Key",
							LK_DECODE_IN_OIM = "Lookup Definition.Lookup Code Information.Decode";
	
	public HashMap<String, String> getLookup(String name) throws tcAPIException, tcInvalidLookupException, tcColumnNotFoundException {
		HashMap<String, String> cache = new HashMap<>();
		tcResultSet result = service.getLookupValues(name);
		for (int i = 0; i < result.getRowCount(); ++i) {
			result.goToRow(i);
			cache.put(result.getStringValue(LK_CODE_IN_OIM),
				result.getStringValue(LK_DECODE_IN_OIM));
		}
		return cache;
	}
	
	public ArrayList<Pair<String, String>> getLookupOrderedNotUnique(String name) throws tcColumnNotFoundException, tcAPIException, tcInvalidLookupException{
		ArrayList<Pair<String, String>> cache = new ArrayList<>();
		tcResultSet result = service.getLookupValues(name);
		for (int i = 0; i < result.getRowCount(); ++i) {
			result.goToRow(i);
			cache.add(new Pair<>(result.getStringValue(LK_CODE_IN_OIM),
				result.getStringValue(LK_DECODE_IN_OIM)));
		}
		return cache;
	}
	
	public HashMap<String, String> clearLookup(String name) throws tcAPIException, tcInvalidLookupException, tcColumnNotFoundException{
		HashMap<String, String> lookup = getLookup(name);
		if(!lookup.isEmpty())
			service.removeBulkLookupValues(name, lookup.keySet());
		return lookup;
	}

	@Override
	protected Class<tcLookupOperationsIntf> getServiceClass() {
		return tcLookupOperationsIntf.class;
	}
	
}
