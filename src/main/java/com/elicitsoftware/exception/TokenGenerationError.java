package com.elicitsoftware.exception;

/**
 * Custom runtime exception thrown when token generation operations fail in the Elicit system.
 * 
 * <p>This exception is used to signal failures during the creation, processing, or validation
 * of authentication tokens, security tokens, or other token-based operations within the
 * Elicit Admin application and related services.</p>
 * 
 * <p><strong>Common Token Generation Scenarios:</strong></p>
 * <ul>
 *   <li><strong>Authentication Tokens:</strong> OIDC/OAuth token generation failures</li>
 *   <li><strong>Session Tokens:</strong> User session token creation errors</li>
 *   <li><strong>API Tokens:</strong> Service-to-service authentication token failures</li>
 *   <li><strong>Temporary Tokens:</strong> Password reset or email verification token issues</li>
 *   <li><strong>Survey Tokens:</strong> Survey access or invitation token generation problems</li>
 * </ul>
 * 
 * <p><strong>Typical Causes:</strong></p>
 * <ul>
 *   <li><strong>Cryptographic Failures:</strong> Key generation or encryption errors</li>
 *   <li><strong>Configuration Issues:</strong> Missing or invalid token configuration</li>
 *   <li><strong>Resource Constraints:</strong> Insufficient system resources for token creation</li>
 *   <li><strong>External Service Failures:</strong> OIDC provider or token service unavailable</li>
 *   <li><strong>Security Policy Violations:</strong> Token parameters violate security policies</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Token generation failure during authentication
 * try {
 *     String token = generateAuthToken(user);
 * } catch (TokenGenerationError e) {
 *     logger.error("Failed to generate auth token for user: " + user.getId(), e);
 *     throw new AuthenticationException("Login failed - please try again");
 * }
 * 
 * // Survey token generation failure
 * try {
 *     String surveyToken = createSurveyInvitationToken(survey, respondent);
 * } catch (TokenGenerationError e) {
 *     logger.error("Failed to generate survey token", e);
 *     // Handle gracefully - perhaps use alternative invitation method
 * }
 * 
 * // API token generation with specific error context
 * if (keyService.isUnavailable()) {
 *     throw new TokenGenerationError("Token generation failed: cryptographic key service unavailable");
 * }
 * }</pre>
 * 
 * <p><strong>Error Handling Best Practices:</strong></p>
 * <ul>
 *   <li><strong>Logging:</strong> Always log token generation failures for security auditing</li>
 *   <li><strong>User Messages:</strong> Provide generic error messages to users (avoid exposing details)</li>
 *   <li><strong>Retry Logic:</strong> Consider retry mechanisms for transient failures</li>
 *   <li><strong>Fallback Options:</strong> Implement alternative authentication or access methods</li>
 *   <li><strong>Security Monitoring:</strong> Monitor for patterns that might indicate attacks</li>
 * </ul>
 * 
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li><strong>Information Disclosure:</strong> Exception messages should not reveal sensitive details</li>
 *   <li><strong>Timing Attacks:</strong> Ensure consistent response times regardless of failure reason</li>
 *   <li><strong>Rate Limiting:</strong> Consider rate limiting token generation to prevent abuse</li>
 *   <li><strong>Audit Trails:</strong> Maintain comprehensive logs of token generation failures</li>
 * </ul>
 * 
 * <p><strong>Integration Points:</strong></p>
 * <ul>
 *   <li><strong>Authentication Services:</strong> OIDC token processing and validation</li>
 *   <li><strong>Survey System:</strong> Survey invitation and access token generation</li>
 *   <li><strong>API Security:</strong> Service authentication and authorization tokens</li>
 *   <li><strong>Session Management:</strong> User session token lifecycle management</li>
 * </ul>
 * 
 * <p>This exception extends {@link RuntimeException}, making it an unchecked exception that
 * can propagate up the call stack without explicit handling requirements. However, it
 * should be caught and handled appropriately at application boundaries to ensure
 * proper error responses and security logging.</p>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see RuntimeException
 * @see javax.crypto.BadPaddingException
 * @see java.security.GeneralSecurityException
 */
public class TokenGenerationError extends RuntimeException {
    
    /** Serial version UID for serialization compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new TokenGenerationError with the specified detail message.
     * 
     * <p>This constructor creates a token generation exception with a descriptive
     * error message that explains the specific cause of the token generation failure.
     * The message should provide enough detail for debugging while avoiding the
     * disclosure of sensitive security information.</p>
     * 
     * <p><strong>Message Guidelines:</strong></p>
     * <ul>
     *   <li><strong>Descriptive:</strong> Clearly indicate the type of token generation failure</li>
     *   <li><strong>Actionable:</strong> Provide context that helps with troubleshooting</li>
     *   <li><strong>Security-Safe:</strong> Avoid exposing sensitive cryptographic details</li>
     *   <li><strong>User-Friendly:</strong> Consider if the message might be shown to users</li>
     * </ul>
     * 
     * <p><strong>Example Messages:</strong></p>
     * <pre>{@code
     * // Good - descriptive but security-safe
     * new TokenGenerationError("Failed to generate authentication token: cryptographic service unavailable");
     * new TokenGenerationError("Survey invitation token creation failed: invalid survey configuration");
     * new TokenGenerationError("API token generation failed: insufficient permissions");
     * 
     * // Avoid - too much security detail
     * new TokenGenerationError("RSA key generation failed with modulus 2048 at offset 0x4F2A");
     * 
     * // Avoid - too vague
     * new TokenGenerationError("Error");
     * }</pre>
     * 
     * <p><strong>Usage Context:</strong></p>
     * <ul>
     *   <li><strong>Authentication Systems:</strong> When OIDC or OAuth token creation fails</li>
     *   <li><strong>Survey Operations:</strong> When survey access tokens cannot be generated</li>
     *   <li><strong>API Security:</strong> When service-to-service tokens fail to create</li>
     *   <li><strong>Session Management:</strong> When user session tokens cannot be established</li>
     * </ul>
     * 
     * <p><strong>Exception Handling:</strong></p>
     * <p>When catching this exception, consider logging the full exception details
     * for security auditing while providing simplified error messages to end users
     * to prevent information disclosure.</p>
     * 
     * @param message the detail message explaining the token generation failure.
     *                Should be descriptive but avoid exposing sensitive security details.
     * @see RuntimeException#RuntimeException(String)
     */
    public TokenGenerationError(String message) {
        super(message);
    }
}
