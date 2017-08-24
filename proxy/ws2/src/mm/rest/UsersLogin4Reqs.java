
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersLogin4Reqs {

    @SerializedName("device_id")
    @Expose
    public String deviceId;
    @SerializedName("login_id")
    @Expose
    public String loginId;
    @SerializedName("password")
    @Expose
    public String password;
    @SerializedName("token")
    @Expose
    public String token;

}
