<img src="https://www.sipgatedesign.com/wp-content/uploads/wort-bildmarke_positiv_2x.jpg" alt="sipgate logo" title="sipgate" align="right" height="112" width="200"/>

# sipgate.io Java OAuth example
To demonstrate how to authenticate against the sipgate REST API using the OAuth mechanism, 
we make use of the `/account` endpoint which provides basic account information. 

> For further information regarding the sipgate REST API please visit https://api.sipgate.com/v2/doc

For educational purposes we do not use an OAuth client library in this example, but if you plan to implement authentication using OAuth in you application we recommend using one. You can find various client libraries here: [https://oauth.net/code/](https://oauth.net/code/).


## What is OAuth and when to use it
OAuth is a standard protocol for authorization. You can find more information on the OAuth website [https://oauth.net/](https://oauth.net/) or on wikipedia [https://en.wikipedia.org/wiki/OAuth](https://en.wikipedia.org/wiki/OAuth).

Applications that use the sipgate REST API on behalf of another user should use the OAuth authentication method instead of Basic Auth.


## Prerequisites
+ JDK 8


## Setup OAuth with sipgate
In order to authenticate against the sipgate REST API via OAuth you first need to create a Client in the sipgate Web App.

You can create a client as follows:

1. Navigate to [console.sipgate.com](https://console.sipgate.com/) and login with your sipgate account credentials.
2. Make sure you are in the **Clients** tab in the left side menu
3. Click the **New client** button
4. Fill out the **New client** dialog (Find information about the Privacy Policy URL and Terms of use URL [here](#privacy-policy-url-and-terms-of-use-url))
5. The **Clients** list should contain your new client
6. Select your client
7. The entries **Id** and **Secret** are `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` required for the configuration of your application (see [Configuration](#configuration))
8. Now you just have to add your `REDIRECT_URI` to your Client by clicking the **Add redirect uri** button and fill in the dialog. In our example we provide a server within the application itself so we use `http://localhost:{port}/oauth` (the default port is `8080`). 

Now your Client is ready to use.


### Privacy Policy URL and Terms of use URL

In the Privacy Policy URL and Terms of use URL you must supply in the **New Client** dialog when creating a new Client to use with OAuth you must supply the Privacy Policy URL and Terms of use URL of the Service you want to use OAuth authorization for. During development and testing you can provide any valid URL but later you must change them.


## Configuration
In the [application.properties](./src/main/resources/application.properties) file located in `<PROJECT_ROOT>/src/main/resources/` insert `YOUR_CLIENT_ID` and `YOUR_CLIENT_SECRET` obtained in Step 7 above:

```  
client_id=YOUR_CLIENT_ID
client_secret=YOUR_CLIENT_SECRET
```

The `oauth_scope` defines what kind of access your Client should have to your account and is specific to your respective application. In this case, since we only want to get your basic account information as an example, the scope `account:read` is sufficient.

```
oauth_scope=account:read
```
> Visit https://developer.sipgate.io/rest-api/oauth2-scopes/ to see all available scopes

The `redirect_uri` which we have previously used in the creation of our Client is supplied to the sipgate login page to specify where you want to be redirected after successful login. As explained above, our application provides a small web server itself that handles HTTP requests directed at `http://localhost:8080/oauth`. In case there is already a service listening on port `8080` of your machine you can choose a different port number, but be sure to adjust both the `redirect_uri` and the `port` property accordingly.

```
redirect_uri=http://localhost:8080/oauth
port=8080
```

## Execution
Navigate to the project's root directory.

Run the application:
```bash
$ ./gradlew run
```


## How It Works
The main function of our application looks like this: 

```java
public static void main(String[] args) {
  ...    
  loadConfiguration();
  ...    
  startServer();
  ...    

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
```

After loading the configuration from the [application.properties](./src/main/resources/application.properties) file we start the web server. We then generate a unique identifier for our authorization process so that we can match a server response to our request later. The authorization URI is composed from the properties previously loaded from the configuration file and printed to the console.

Opening the link in your browser takes you to the sipgate login page where you need to confirm the scope that your Client is requesting access to before logging in with your sipgate credentials. You are then redirected to `http://localhost:8080/oauth` and our application's web server receives your request. The corresponding logic is contained in the `handleRequest` method:

```java
private static void handleRequest(HttpExchange exchange) {
  Map<String, String> queryParams = getQueryParams(exchange);
  if (!state.equals(queryParams.get("state"))) {
    System.err.println("State in the callback does not match the state in the original request");
    return;
  }

  ...

  exchange.sendResponseHeaders(204, -1);

  TokenResponse tokenResponse = retrieveToken(queryParams.get("code"));
  retrieveAccountInformation(tokenResponse.accessToken);

  TokenResponse refreshedToken = refreshToken(tokenResponse.refreshToken);
  retrieveAccountInformation(refreshedToken.accessToken);

  ... 
  
  exchange.close();
}

```
After extracting the query parameters from the request received from the browser, we verify that the state transmitted by the authorization server matched the one initially supplied. In the case of multiple concurrent authorization processes this state also serves to match pairs of request and response. We use the code obtained from the request to fetch a set of tokens from the authorization server and try them out by making an authorize request to the `/account` endpoint of the REST API. Lastly, we use the refresh token to obtain another set of tokens. Note that this invalidates the previous set.

The `retrieveToken` method fetches the token from the authorization server.
```java
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
```
First we construct the `tokenUrl` from the `baseUrl` and the `tokenEndpoint`. After that we use Unirest to send a POST-Request to the authorization server to obtain a set of tokens (Access-Token and Refresh-Token). The POST-Request must contain the `client_id`, `client_secret`, `redirect_uri`, `code` and `grant_type` as form data.

The `refreshToken` method is very similar to the `retrieveToken` method. It differs in that we set the `grant_type` to `refresh_token` to indicate that we want to refresh our token, and provide the `refresh_token` we got from the `retrieveToken` method instead of the `code`.
> ```java
> [...]
> .field("refresh_token", refreshToken)
> .field("grant_type", "refresh_token")				
> [...]
> ```

To see if authorization with the token works, we query the `/account` endpoint of the REST API.
```java
private static void retrieveAccountInformation(String accessToken) throws UnirestException {
  String accountInformation = Unirest.get("https://api.sipgate.com/v2/account")
    .header("Authorization", "Bearer " + accessToken)
    .asString()
    .getBody();
  System.out.println(accountInformation);
}
```
To use the token for authorization we set the `Authorization` header to `Bearer ` followed by a space and the `accessToken` we obtained with the `retrieveToken` or `refreshToken` method.


## Common Issues

### "State in the callback does not match the state in the original request"
Possible reasons are:
- the application was restarted and you used old url again or refreshed the browser tab


### "java.net.BindException: Address already in use"
Possible reasons are:
- another instance of the application is running
- the port configured in the [application.properties](./src/main/resources/application.properties) file is used by another application.


### "java.net.SocketException: Permission denied"
Possible reasons are:
- you do not have the permission to bind to the specified port. This can happen if you use port 80, 443 or another well-known port which you can only bind to if you run the application with superuser privileges.


### "invalid parameter: redirect_uri"
Possible reasons are:
- the redirect_uri in the [application.properties](./src/main/resources/application.properties) is invalid or not set
- the redirect_uri is not correctly configured the sipgate Web App (You can find more information about the configuration process in the [Setup OAuth with sipgate](#setup-oauth-with-sipgate) section)


### "client not found" or "invalid client_secret"
Possible reasons are:
- the client_id or client_secret configured in the [application.properties](./src/main/resources/application.properties) is invalid. You can check them in the sipgate Web App. See [Setup OAuth with sipgate](#setup-oauth-with-sipgate)


## Related
+ [OAuth RFC6749](https://tools.ietf.org/html/rfc6749)
+ [oauth.net](https://oauth.net/)
+ [auth0.com/docs/](https://auth0.com/docs/)
+ [Unirest documentation](http://unirest.io/)


## Contact Us
Please let us know how we can improve this example. 
If you have a specific feature request or found a bug, please use **Issues** or fork this repository and send a **pull request** with your improvements.


## License
This project is licensed under **The Unlicense** (see [LICENSE file](./LICENSE)).


## External Libraries
This code uses the following external libraries

+ unirest:
  + Licensed under the [MIT License](https://opensource.org/licenses/MIT)
  + Website: http://unirest.io/java

+ jackson:
  + Licensed under the [Apache-2.0](https://opensource.org/licenses/Apache-2.0)
  + Website: https://github.com/FasterXML/jackson

+ HttpComponents:
  + Licensed under the [Apache-2.0](https://opensource.org/licenses/Apache-2.0)
  + Website: https://hc.apache.org/index.html


----
[sipgate.io](https://www.sipgate.io) | [@sipgateio](https://twitter.com/sipgateio) | [API-doc](https://api.sipgate.com/v2/doc)
