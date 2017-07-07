
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersxReps {

    @SerializedName("users")
    @Expose
    public List<User> users = new ArrayList<User>();

}
