
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersPassword {

    @SerializedName("current_password")
    @Expose
    public String currentPassword;
    @SerializedName("new_password")
    @Expose
    public String newPassword;

}
