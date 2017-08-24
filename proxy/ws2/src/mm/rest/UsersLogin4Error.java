
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersLogin4Error {

    @SerializedName("id")
    @Expose
    public String id;
    @SerializedName("message")
    @Expose
    public String message;
    @SerializedName("detailed_error")
    @Expose
    public String detailedError;
    @SerializedName("request_id")
    @Expose
    public String requestId;
    @SerializedName("status_code")
    @Expose
    public long statusCode;

}
