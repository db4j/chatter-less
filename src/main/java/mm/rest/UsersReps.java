
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersReps {

    @SerializedName("id")
    @Expose
    public String id;
    @SerializedName("create_at")
    @Expose
    public int createAt;
    @SerializedName("update_at")
    @Expose
    public int updateAt;
    @SerializedName("delete_at")
    @Expose
    public int deleteAt;
    @SerializedName("username")
    @Expose
    public String username;
    @SerializedName("auth_data")
    @Expose
    public String authData;
    @SerializedName("auth_service")
    @Expose
    public String authService;
    @SerializedName("email")
    @Expose
    public String email;
    @SerializedName("nickname")
    @Expose
    public String nickname;
    @SerializedName("first_name")
    @Expose
    public String firstName;
    @SerializedName("last_name")
    @Expose
    public String lastName;
    @SerializedName("position")
    @Expose
    public String position;
    @SerializedName("roles")
    @Expose
    public String roles;
    @SerializedName("allow_marketing")
    @Expose
    public boolean allowMarketing;
    @SerializedName("notify_props")
    @Expose
    public NotifyUsers notifyProps;
    @SerializedName("last_password_update")
    @Expose
    public int lastPasswordUpdate;
    @SerializedName("locale")
    @Expose
    public String locale;

}
