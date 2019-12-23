package sipgateio.oauth;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

public class App {

	private static Properties properties = new Properties();

	private static String baseUrl;
	private static String authEndpoint;
	private static String clientId;
	private static String redirectUri;
	private static String oauthScope;
	private static String tokenEndpoint;
	private static String clientSecret;
	private static String state;
	private static String port;

	public static void main(String[] args) {
		Unirest.setObjectMapper(new TokenResponseMapper());

		try {
			loadConfiguration();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		try {
			startServer();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		state = UUID.randomUUID().toString();
		URIBuilder authUrl = new URIBuilder()
				.setScheme("https")
				.setHost(baseUrl)
				.setPath(authEndpoint)
				.addParameter("client_id", clientId)
				.addParameter("redirect_uri", redirectUri)
				.addParameter("scope", oauthScope)
				.addParameter("state", state)
				.addParameter("response_type", "code");

		System.out.println("Please open the following URL in your browser: \n" + authUrl);
	}

	private static void loadConfiguration() throws IOException {
		properties.load(App.class.getClassLoader().getResourceAsStream("application.properties"));

		baseUrl = properties.getProperty("base_url");
		authEndpoint = properties.getProperty("auth_endpoint");
		clientId = properties.getProperty("client_id");
		clientSecret = properties.getProperty("client_secret");
		redirectUri = properties.getProperty("redirect_uri");
		oauthScope = properties.getProperty("oauth_scope");
		tokenEndpoint = properties.getProperty("token_endpoint");
		port = properties.getProperty("port");
	}

	private static void startServer() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(port)), 0);
		HttpContext context = server.createContext("/oauth");
		context.setHandler(App::handleRequest);
		server.start();
	}

	private static void handleRequest(HttpExchange exchange) {
		Map<String, String> queryParams = getQueryParams(exchange);
		if (!state.equals(queryParams.get("state"))) {
			System.err.println("State in the callback does not match the state in the original request");
			return;
		}

		try {
			exchange.sendResponseHeaders(204, -1);

			TokenResponse tokenResponse = retrieveToken(queryParams.get("code"));
			retrieveAccountInformation(tokenResponse.accessToken);

			TokenResponse refreshedToken = refreshToken(tokenResponse.refreshToken);
			retrieveAccountInformation(refreshedToken.accessToken);

		} catch (UnirestException | IOException e) {
			e.printStackTrace();
		} finally {
			exchange.close();
		}
	}

	private static Map<String, String> getQueryParams(HttpExchange exchange) {
		List<NameValuePair> queryParams = new URIBuilder(exchange.getRequestURI())
				.getQueryParams();
		return queryParams.stream()
				.collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
	}

	private static void retrieveAccountInformation(String accessToken) throws UnirestException {
		String accountInformation = Unirest.get("https://api.sipgate.com/v2/account")
				.header("Authorization", "Bearer " + accessToken)
				.asString()
				.getBody();
		System.out.println(accountInformation);
	}

	private static TokenResponse refreshToken(String refreshToken) throws UnirestException {
		System.out.println("Refreshing token");

		String tokenUrl = new URIBuilder()
				.setScheme("https")
				.setHost(baseUrl)
				.setPath(tokenEndpoint)
				.toString();

		TokenResponse responseToken = Unirest
				.post(tokenUrl)
				.field("client_id", clientId)
				.field("client_secret", clientSecret)
				.field("refresh_token", refreshToken)
				.field("grant_type", "refresh_token")
				.asObject(TokenResponse.class)
				.getBody();

		if (responseToken.error != null) {
			System.err.println(responseToken.errorDescription);
			return null;
		}

		System.out.println(String.format("Received new token: %s", responseToken.accessToken));
		return responseToken;
	}

	private static TokenResponse retrieveToken(String code) throws UnirestException {
		String tokenUrl = new URIBuilder()
				.setScheme("https")
				.setHost(baseUrl)
				.setPath(tokenEndpoint)
				.toString();

		TokenResponse tokenResponse = Unirest
				.post(tokenUrl)
				.field("client_id", clientId)
				.field("client_secret", clientSecret)
				.field("redirect_uri", redirectUri)
				.field("code", code)
				.field("grant_type", "authorization_code")
				.asObject(TokenResponse.class)
				.getBody();

		if (tokenResponse.error != null) {
			System.err.println(tokenResponse.errorDescription);
			return null;
		}
		
		System.out.println(String.format("Received new token: %s", tokenResponse.accessToken));
		return tokenResponse;
	}
}
