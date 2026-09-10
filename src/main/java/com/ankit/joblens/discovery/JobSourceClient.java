package com.ankit.joblens.discovery;

import com.ankit.joblens.searchprofile.SearchProfile;

public interface JobSourceClient {

  boolean supports(JobSource source);

  default int pageSize() {
    return 20;
  }

  default boolean supportsQueryBroadening() {
    return false;
  }

  JobPage search(SearchProfile profile, PageRequest request);
}
