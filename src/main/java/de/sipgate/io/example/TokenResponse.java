package de.sipgate.io.example;

import com.fasterxml.jackson.annotation.JsonAlias;

public class TokenResponse {
	@JsonAlias("session_state")
	public String sessionState;
	@JsonAlias("token_type")
	public String tokenType;
	@JsonAlias("refresh_token")
	public String refreshToken;
	@JsonAlias("access_token")
	public String accessToken;
	@JsonAlias("not-before-policy")
	public String notBeforePolicy;
	@JsonAlias("expires_in")
	public String expiresIn;
	@JsonAlias("refresh_expires_in")
	public String refreshExpiresIn;
	public String scope;
	public String error;
	@JsonAlias("error_description")
	public String errorDescription;
}
