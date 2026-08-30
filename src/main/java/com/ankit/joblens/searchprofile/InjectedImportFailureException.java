package com.ankit.joblens.searchprofile;

public class InjectedImportFailureException extends RuntimeException {

  public InjectedImportFailureException(long rowNumber) {
    super("Controlled import failure on CSV row " + rowNumber);
  }
}
