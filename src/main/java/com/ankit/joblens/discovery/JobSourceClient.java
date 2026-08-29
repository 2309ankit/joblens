package com.ankit.joblens.discovery;

import com.ankit.joblens.searchprofile.SearchProfile;

public interface JobSourceClient {

    boolean supports(JobSource source);

    JobPage search(SearchProfile profile, PageRequest request);
}
