package com.elicitsoftware.admin.upload;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;

import java.io.InputStream;

/**
 * Data transfer object for handling multipart form data uploads in the Elicit Admin application.
 *
 * <p>This class is designed to work with JAX-RS and RESTEasy Reactive to handle file uploads
 * along with associated metadata. It uses form parameters with specific media types to properly
 * parse multipart/form-data requests containing file uploads and related information.</p>
 *
 * <p>The class supports the following upload scenarios:</p>
 * <ul>
 *   <li><strong>File Upload:</strong> Binary file data with original filename</li>
 *   <li><strong>User Context:</strong> Username for associating uploads with specific users</li>
 *   <li><strong>Survey Association:</strong> Survey ID for linking uploads to surveys</li>
 *   <li><strong>Department Context:</strong> Department ID for organizational grouping</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @POST
 * @Path("/upload")
 * @Consumes(MediaType.MULTIPART_FORM_DATA)
 * public Response uploadFile(MultipartBody body) {
 *     // Process file upload
 *     InputStream fileData = body.file;
 *     String fileName = body.fileName;
 *     // ... process upload
 * }
 * }</pre>
 *
 * <p><strong>Technical Notes:</strong></p>
 * <ul>
 *   <li>Uses {@link InputStream} for efficient handling of large file uploads</li>
 *   <li>Survey and department IDs are String types due to Quarkus issue #8239</li>
 *   <li>All fields are public for direct JAX-RS injection</li>
 *   <li>Compatible with RESTEasy Reactive multipart processing</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see jakarta.ws.rs.FormParam
 * @see org.jboss.resteasy.reactive.PartType
 */
public class MultipartBody {

    /**
     * The uploaded file data as an input stream.
     *
     * <p>This field receives the binary content of the uploaded file through
     * the "file" form parameter. The data is provided as an {@link InputStream}
     * for memory-efficient processing of potentially large files.</p>
     *
     * <p><strong>Form Parameter:</strong> {@code file}</p>
     * <p><strong>Content Type:</strong> {@code application/octet-stream}</p>
     *
     * <p><strong>Important:</strong> The stream should be properly closed after
     * processing to prevent resource leaks. Consider using try-with-resources
     * or ensuring proper cleanup in exception handling.</p>
     *
     * @see InputStream
     */
    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream file;

    /**
     * The original filename of the uploaded file.
     *
     * <p>This field contains the filename as provided by the client during upload.
     * It preserves the original file extension and name for proper file handling
     * and storage operations.</p>
     *
     * <p><strong>Form Parameter:</strong> {@code fileName}</p>
     * <p><strong>Content Type:</strong> {@code text/plain}</p>
     *
     * <p><strong>Usage Notes:</strong></p>
     * <ul>
     *   <li>Should be validated for security (path traversal, illegal characters)</li>
     *   <li>May be used for determining file type based on extension</li>
     *   <li>Can be modified before storage if needed for naming conventions</li>
     * </ul>
     */
    @FormParam("fileName")
    @PartType(MediaType.TEXT_PLAIN)
    public String fileName;

    /**
     * The username of the user performing the upload.
     *
     * <p>This field identifies which user is uploading the file, enabling
     * proper authorization checks and audit logging. The username should
     * correspond to a valid user account in the system.</p>
     *
     * <p><strong>Form Parameter:</strong> {@code username}</p>
     * <p><strong>Content Type:</strong> {@code text/plain}</p>
     *
     * <p><strong>Security Considerations:</strong></p>
     * <ul>
     *   <li>Should be validated against authenticated session</li>
     *   <li>Used for authorization and access control</li>
     *   <li>Required for audit trails and ownership tracking</li>
     * </ul>
     */
    @FormParam("username")
    @PartType(MediaType.TEXT_PLAIN)
    public String username;

    /**
     * The survey identifier to associate with the uploaded file.
     *
     * <p>This field links the uploaded file to a specific survey in the system.
     * The survey ID enables proper categorization and retrieval of uploaded
     * files within the context of survey data collection.</p>
     *
     * <p><strong>Form Parameter:</strong> {@code surveyId}</p>
     * <p><strong>Content Type:</strong> {@code text/plain}</p>
     * <p><strong>Data Type:</strong> String (should represent a valid Long ID)</p>
     *
     * <p><strong>Technical Note:</strong> This field uses String type instead of
     * Long due to a known issue in Quarkus (issue #8239) with multipart form
     * processing of numeric types. The value should be converted to Long when
     * needed for database operations.</p>
     *
     * @see <a href="https://github.com/quarkusio/quarkus/issues/8239">Quarkus Issue #8239</a>
     */
    @FormParam("surveyId")
    @PartType(MediaType.TEXT_PLAIN)
    public String surveyId;

    /**
     * The department identifier for organizational context of the upload.
     *
     * <p>This field associates the uploaded file with a specific department,
     * enabling proper access control and organizational grouping of uploaded
     * content. Department assignment affects who can access and manage the
     * uploaded files.</p>
     *
     * <p><strong>Form Parameter:</strong> {@code departmentId}</p>
     * <p><strong>Content Type:</strong> {@code text/plain}</p>
     * <p><strong>Data Type:</strong> String (should represent a valid Long ID)</p>
     *
     * <p><strong>Access Control:</strong></p>
     * <ul>
     *   <li>Used for department-based authorization</li>
     *   <li>Determines which users can access uploaded files</li>
     *   <li>Required for proper data segregation between departments</li>
     * </ul>
     *
     * <p><strong>Technical Note:</strong> This field uses String type instead of
     * Long due to a known issue in Quarkus (issue #8239) with multipart form
     * processing of numeric types. The value should be converted to Long when
     * needed for database operations.</p>
     *
     * @see <a href="https://github.com/quarkusio/quarkus/issues/8239">Quarkus Issue #8239</a>
     */
    @FormParam("departmentId")
    @PartType(MediaType.TEXT_PLAIN)
    public String departmentId;

    /**
     * Default constructor for JAX-RS multipart processing.
     * <p>
     * Creates a new MultipartBody instance for form data binding.
     * This constructor is used by JAX-RS frameworks for automatic
     * instantiation during multipart form processing.
     */
    public MultipartBody() {
        // Default constructor for JAX-RS
    }
}
