package sipgateio.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class TokenResponseMapper implements com.mashape.unirest.http.ObjectMapper {
	private ObjectMapper jacksonObjectMapper = new ObjectMapper();

	public <T> T readValue(String value, Class<T> valueType) {
		try {
			return jacksonObjectMapper.readValue(value, valueType);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String writeValue(Object value) {
		try {
			return jacksonObjectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
