package gov.nara.api.poc.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApiUtil {

	@Autowired
	private Environment env;

	private static Map<String, List<String>> usersMap;

	private final Logger logger = LoggerFactory.getLogger(ApiUtil.class);

	private ReentrantLock lock;

	public ApiUtil() {

		usersMap = new HashMap<String, List<String>>();
		lock = new ReentrantLock();

	}

	public void clearCookies() {

		usersMap = new HashMap<String, List<String>>();

	}

	public List<String> getCookies(String userName) {

		lock.lock();
		List<String> cookie = new ArrayList<String>();

		try {
			if (usersMap.containsKey(userName))
				cookie = usersMap.get(userName);
			else
				cookie = addNewCookies(userName);

		} catch (IllegalMonitorStateException e) {
			logger.error("userName: " + userName + " >>> ERROR (IllegalMonitorStateException) :"
					+ e.getMessage());
		} catch (Exception e) {
			logger.error("userName: " + userName + " >>> ERROR (Exception) :" + e.getMessage());
		} finally {
			lock.unlock();
		}

		return cookie;

	}

	List<String> addNewCookies(String userName) {

		logger.info("try login for user: " + userName);
		List<String> cookie = getLogin(userName, env.getProperty("nara.era2.user.login.password"));

		if (!cookie.isEmpty())
			usersMap.put(userName, cookie);
		else
			logger.error("ERROR in login for username: " + userName);

		return cookie;

	}

	List<String> getLogin(String userName, String password) {

		List<String> cookie = new ArrayList<String>();

		CloseableHttpClient client = HttpClients.createDefault();

		String result, str;

		HttpPost httpPost = new HttpPost(this.env.getProperty("nara.era2.serverURL")
				+ this.env.getProperty("nara.era2.webappsdpe") + "security/auth-force");
		JSONObject body = new JSONObject();
		JSONObject resultJson = new JSONObject();

		body.put("username", userName);
		body.put("password", password);
		body.put("acknowledgement", true);

		StringEntity entity;
		try {
			entity = new StringEntity(body.toString());

			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			CloseableHttpResponse response = client.execute(httpPost);

			if (response.containsHeader("Set-Cookie")) {

				for (Header h : response.getHeaders("Set-Cookie")) {
					str = h.getValue().toString();
					if (str.contains("Domain=."))
						str = str.replace("Domain=.", "Domain=");
					cookie.add(str);
				}

			}

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 200) {

				resultJson = new JSONObject(result);

				if (resultJson.has("loginStatus")) {

					if (resultJson.getString("loginStatus").equals("OK")) {
						logger.info("Username (" + userName + ") successfully login");
						return cookie;
					} else {
						logger.error("ERROR in Login (loginStatus not OK): " + result);
					}
				} else {
					logger.error("ERROR in Login (no loginStatus): " + result);
				}

			} else {
				logger.error("ERROR in Login (not 200): " + result);
			}

		} catch (UnsupportedEncodingException ex) {
			logger.error("ERROR ex in Login: " + ex.getMessage());
		} catch (IOException ex) {
			logger.error("ERROR ex in Login: " + ex.getMessage());
		}

		return new ArrayList<String>();
	}

}
