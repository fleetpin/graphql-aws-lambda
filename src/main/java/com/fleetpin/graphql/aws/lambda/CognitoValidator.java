/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.fleetpin.graphql.aws.lambda;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fleetpin.graphql.builder.SchemaBuilder;

/**
 * seems madness that AWS does not have this in a library maybe I missed something...
 * @author Ashley Taylor
 *
 */
public class CognitoValidator {

	private JWTVerifier jwtVerifier;


	public CognitoValidator(String region, String userPoolsId) {
		RSAKeyProvider keyProvider = new AwsCognitoRSAKeyProvider(region, userPoolsId);
		Algorithm algorithm = Algorithm.RSA256(keyProvider);
		this.jwtVerifier = JWT.require(algorithm)
		    .build();
	}

	
	
	public JsonNode verify(String token) {
		var verified = jwtVerifier.verify(token);
		try {
			return SchemaBuilder.MAPPER.readTree(Base64.getDecoder().decode(verified.getPayload()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	
	public static class AwsCognitoRSAKeyProvider implements RSAKeyProvider {

	    private final URL aws_kid_store_url;

	    public AwsCognitoRSAKeyProvider(String aws_cognito_region, String aws_user_pools_id) {
	        String url = String.format("https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json", aws_cognito_region, aws_user_pools_id);
	        try {
	            this.aws_kid_store_url = new URL(url);
	        } catch (MalformedURLException e) {
	            throw new RuntimeException(String.format("Invalid URL provided, URL=%s", url));
	        }
	    }


	    @Override
	    public RSAPublicKey getPublicKeyById(String kid) {
	        try {
	            JwkProvider provider = new JwkProviderBuilder(aws_kid_store_url).build();
	            Jwk jwk = provider.get(kid);
	            return (RSAPublicKey) jwk.getPublicKey();
	        } catch (Exception e) {
	            throw new RuntimeException(String.format("Failed to get JWT kid=%s from aws_kid_store_url=%s", kid, aws_kid_store_url));
	        }
	    }

	    @Override
	    public RSAPrivateKey getPrivateKey() {
	        return null;
	    }

	    @Override
	    public String getPrivateKeyId() {
	        return null;
	    }
	}
}
