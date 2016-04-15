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
import Thor.API.tcResultSet;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public final class Misc {
	
	public static final String OPERATION_SUCCESS_VALUE = "COMPLETED",
			MAIL_SERVER_ADDRESS_PARAM = "Server Name",
			MAIL_SERVER_USER_PARAM = "User Login",
			ORG_MAIN_NAME = "Xellerate Users",
			OIM_REQUEST_LINK = "/identity/faces/home?tf=request_details&requestId=";
	
	public static boolean isNullOrEmpty(String str) {
		return str == null || str.isEmpty();
	}

	public static boolean isNullOrEmpty(Map m) {
		return m == null || m.isEmpty();
	}

	public static String nullToEmpty(String str) {
		return str == null ? "" : str;
	}

	public static boolean isNullOrEmpty(Collection c) {
		return c == null || c.isEmpty();
	}
	
	public static boolean isNullOrEmpty(tcResultSet res) throws tcAPIException {
		return res == null || res.isEmpty();
	}

	public static void registerJDBC(String driverName) throws SQLException,
															  ClassNotFoundException,
															  InstantiationException,
															  IllegalAccessException {
		Driver loadDriver = (Driver) Class.forName(driverName).newInstance();
		Enumeration<Driver> drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements()) {
			Driver driver = drivers.nextElement();
			if (driver.acceptsURL(driverName)
				&& driverName.equalsIgnoreCase(driver.getClass().getCanonicalName())) {
				return;
			}
		}
		DriverManager.registerDriver(loadDriver);
	}

	public static void trimStrings(HashMap<String, Object> in) {
		for (String s : in.keySet()) {
			Object o = in.get(s);
			if (o instanceof String) {
				in.put(s, ((String) o).trim());
			}
		}
	}

	public static String ownStack(Throwable t) {
		StringBuilder sb = new StringBuilder("Exception ");
		final String OWN_PACKAGE = "tk.sot_tech";
		sb.append(t.toString()).append(":\n");
		StackTraceElement[] stackTrace = t.getStackTrace();
		if (stackTrace != null) {
			for (StackTraceElement ste : stackTrace) {
				if (ste != null && nullToEmpty(ste.getClassName()).startsWith(OWN_PACKAGE)) {
					if (sb.charAt(sb.length() - 1) == '.') {
						sb.append("\n");
					}
					sb.append("\t@").append(ste.toString()).append('\n');
				}
				else {
					if (sb.charAt(sb.length() - 1) == '\n') {
						sb.append("\t");
					}
					sb.append('.');
				}
			}
		}
		return sb.toString();
	}
	
	public static boolean equalsAny(String eq, String[] variants, boolean ignoreCase) {
		if (isNullOrEmpty(eq) || variants == null) {
			return false;
		}
		else {
			for (String s : variants) {
				if (!isNullOrEmpty(s) && (ignoreCase ? eq.equalsIgnoreCase(s) : eq.equals(s))) {
					return true;
				}
			}
		}
		return false;
	}

	public static ResourceBundle getBundleFromFs(String propertyFile) throws MalformedURLException {
		File parent = new File(new File(propertyFile).getParent());
		File file = new File(propertyFile);
		URL[] urls = {parent.toURI().toURL()};
		ClassLoader loader = new URLClassLoader(urls);
		return ResourceBundle.getBundle(file.getName().replace(".properties", ""), Locale.getDefault(), loader);
	}
	
	public static boolean toBoolean(String str){
		return "1".equals(str) || "true".equalsIgnoreCase(str) || "yes".equalsIgnoreCase(str);
	}
	
	public static String dbFieldToApiField(String field) {
		return field.toUpperCase().replaceAll(".*_UDF_", "");
	}

}
