package de.unileipzig.bioinf.pdb2dllearner;

import java.io.File;
import java.io.FileFilter;

public class ArffFileFilter implements FileFilter
{
  private final String extension = new String("arff");

  public boolean accept(File file)
  {
    return file.getName().toLowerCase().endsWith(extension);
  }
}