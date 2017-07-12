
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersAutocompleteInTeamInChannelNameSeReps {

    @SerializedName("users")
    @Expose
    public List<User> users = new ArrayList<User>();

}
