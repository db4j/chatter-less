
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsxPostsSearchReps {

    @SerializedName("order")
    @Expose
    public List<Object> order = new ArrayList<Object>();
    @SerializedName("posts")
    @Expose
    public Posts posts;

}
