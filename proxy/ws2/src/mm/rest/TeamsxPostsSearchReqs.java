
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsxPostsSearchReqs {

    @SerializedName("terms")
    @Expose
    public String terms;
    @SerializedName("is_or_search")
    @Expose
    public boolean isOrSearch;

}
