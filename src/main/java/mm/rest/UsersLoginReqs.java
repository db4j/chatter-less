
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersLoginReqs {

    @SerializedName("id")
    @Expose
    public String id;
    @SerializedName("password")
    @Expose
    public String password;
    @SerializedName("token")
    @Expose
    public String token;

}
