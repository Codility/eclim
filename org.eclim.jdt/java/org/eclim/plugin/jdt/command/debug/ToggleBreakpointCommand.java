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
package org.eclim.plugin.jdt.command.debug;

import java.util.HashMap;
import java.util.Map;

import org.eclim.annotation.Command;

import org.eclim.command.CommandLine;
import org.eclim.command.DebugOptions;
import org.eclim.command.Options;

import org.eclim.logging.Logger;

import org.eclim.plugin.core.command.AbstractCommand;

import org.eclim.plugin.jdt.util.JavaUtils;

import org.eclipse.core.resources.IResource;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;

/**
 * Command to add, remove a specific breakpoint.
 */
@Command(
  name = "java_debug_toggle_breakpoint",
  options =
    "REQUIRED p project ARG," +
    "REQUIRED f file ARG," +
    "REQUIRED l line_num ARG"
)
public class ToggleBreakpointCommand extends AbstractCommand
{
  private static final Logger logger = Logger.getLogger(
      ToggleBreakpointCommand.class);

  @Override
  public Object execute(CommandLine commandLine) throws Exception
  {
    if (logger.isInfoEnabled()) {
      logger.info("Command: " + commandLine);
    }

    String projectName = commandLine.getValue(Options.PROJECT_OPTION);
    String fileName = commandLine.getValue(Options.FILE_OPTION);
    int lineNum = Integer.parseInt(commandLine.getValue(
          DebugOptions.LINE_NUM_OPTION));

    toggleBreakpoint(projectName, fileName, lineNum);

    return null;
  }

  private void toggleBreakpoint(String projectName, String fileName,
      int lineNum) throws Exception
  {

    ICompilationUnit compUnit = JavaUtils.getCompilationUnit(projectName, fileName);
    // TODO How to find out right type from this array?
    IType type = compUnit.getTypes()[0];
    IResource res = compUnit.getResource();
    Map<String, Object> attrMap = new HashMap<String, Object>();

    String typeName = type.getFullyQualifiedName();
    IJavaLineBreakpoint breakpoint = JDIDebugModel.lineBreakpointExists(res,
        typeName, lineNum);

    if (breakpoint == null) {
      JDIDebugModel.createLineBreakpoint(res, typeName, lineNum, -1, -1, 0, true,
          attrMap);
    } else {
      IBreakpointManager breakpointMgr = DebugPlugin.getDefault()
        .getBreakpointManager();
      breakpointMgr.removeBreakpoint(breakpoint, true);
    }

    if (logger.isInfoEnabled()) {
      logger.info("Created breakpoint: " + fileName + " at " + lineNum);
    }
  }
}
