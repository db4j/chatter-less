
package mm.ws.server;

import mm.rest.User;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UserUpdatedData {

    @SerializedName("user")
    @Expose
    public User user;

    /**
     * No args constructor for use in serialization
     * 
     */
    public UserUpdatedData() {
    }

    /**
     * 
     * @param user
     */
    public UserUpdatedData(User user) {
        super();
        this.user = user;
    }

}
