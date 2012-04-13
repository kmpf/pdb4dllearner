package de.unileipzig.bioinf.pdb2dllearner;

import java.io.File;
import java.io.FileFilter;

public class ConfFileFilter implements FileFilter
{
  private final String extension = new String("conf");

  public boolean accept(File file)
  {
    return file.getName().toLowerCase().endsWith(extension);
  }
}

