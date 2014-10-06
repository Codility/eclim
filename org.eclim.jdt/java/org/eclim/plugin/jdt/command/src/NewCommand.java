/**
 * Copyright (C) 2005 - 2014  Eric Van Dewoestine
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.eclim.plugin.jdt.command.src;

import java.io.File;

import java.util.HashMap;

import org.eclim.annotation.Command;

import org.eclim.command.CommandLine;
import org.eclim.command.Options;

import org.eclim.plugin.core.command.AbstractCommand;

import org.eclim.plugin.jdt.util.JavaUtils;

import org.eclipse.core.resources.IResource;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Command to generate a new Java Type file
 *
 * @author Daniel Leong
 */
@Command(
  name = "java_new",
  options =
    "REQUIRED p project ARG," +
    "REQUIRED t type ARG," +
    "REQUIRED n name ARG "
)
public class NewCommand
  extends AbstractCommand
{

  static final String TEMPLATE =
    "package %1$s;\n\n" +
    "public %2$s %3$s {\n" +
    "}";

  @Override
  public Object execute(CommandLine commandLine)
    throws Exception
  {
    String project = commandLine.getValue(Options.PROJECT_OPTION);
    String type = commandLine.getValue(Options.TYPE_OPTION);
    String name = commandLine.getValue(Options.NAME_OPTION);

    int classStart = name.lastIndexOf('.');
    final String packageName = classStart >= 0 ?
      name.substring(0, classStart) : name;
    final String typeName = classStart >= 0 ?
      name.substring(classStart + 1) : name;
    final String fileName = typeName + ".java";

    IPackageFragment frag = JavaUtils.getPackageFragment(project, packageName);
    if (frag == null) {
      // TODO support generating a new package?
      throw new IllegalStateException("Could not get package");
    }

    // locate the to-be created file
    File fragmentPath = frag.getUnderlyingResource()
      .getLocation().toFile();
    final File file = new File(fragmentPath, fileName);

    // make sure eclipse is up to date, in case the user
    //  deleted the file outside of eclipse's knowledge
    frag.getUnderlyingResource()
      .refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());

    // create!
    final String content = String.format(TEMPLATE,
        packageName, getType(type), typeName);
    try {
      // NB: If we delete the file outside of Eclipse'
      //  awareness, it will whine if we then try to
      //  recreate it. So, if we *know* the file doesn't
      //  exist, force it.
      ICompilationUnit unit = frag.createCompilationUnit(
            fileName,
            content,
            false,
            new NullProgressMonitor());

      if (unit == null || !file.exists()) {
        return singleMap("error", "Could not create " + file);
      }

    } catch (JavaModelException e) {
      return singleMap("error", e.getMessage());
    }

    return singleMap("path", file.getAbsolutePath());
  }

  private HashMap<String, Object> singleMap(String key, Object value)
  {
    HashMap<String, Object> result = new HashMap<String, Object>();
    result.put(key, value);
    return result;
  }

  private String getType(String type)
  {
    if ("abstract".equals(type)) {
      return "abstract class";
    }

    return type;
  }

}
