
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersxTeamsRep {

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
    @SerializedName("display_name")
    @Expose
    public String displayName;
    @SerializedName("name")
    @Expose
    public String name;
    @SerializedName("description")
    @Expose
    public String description;
    @SerializedName("email")
    @Expose
    public String email;
    @SerializedName("type")
    @Expose
    public String type;
    @SerializedName("company_name")
    @Expose
    public String companyName;
    @SerializedName("allowed_domains")
    @Expose
    public String allowedDomains;
    @SerializedName("invite_id")
    @Expose
    public String inviteId;
    @SerializedName("allow_open_invite")
    @Expose
    public boolean allowOpenInvite;

}
