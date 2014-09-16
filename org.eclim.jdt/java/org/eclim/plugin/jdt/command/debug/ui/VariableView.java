/**
 * Copyright (C) 2014  Eric Van Dewoestine
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
package org.eclim.plugin.jdt.command.debug.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclim.logging.Logger;

import org.eclipse.debug.core.DebugException;

import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;

import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;

import org.eclipse.jdt.internal.debug.core.logicalstructures.JDIAllInstancesValue;

import org.eclipse.jdt.internal.debug.core.model.JDIReferenceListValue;
import org.eclipse.jdt.internal.debug.core.model.JDIValue;
import org.eclipse.jdt.internal.debug.core.model.JDIVariable;

import org.eclipse.osgi.util.NLS;

/**
 * UI model for displaying variables.
 *
 * <p>
 * The formatting code is borrowed from Eclipse JDT UI.
 * @see org.eclipse.jdt.internal.debug.ui.JDIModelPresentation
 */
public class VariableView
{
  private static final Logger logger =
    Logger.getLogger(VariableView.class);

  /**
   * Depth of the root node.
   */
  private static final int ROOT_DEPTH = 0;

  private Map<Long, ExpandableVar> expandableVarMap =
    new HashMap<Long, ExpandableVar>();

  /**
   * Variable value that is shown in UI and is expandable; i.e., has inner
   * variables/fields. These values are instances of IJavaObject.
   */
  private class ExpandableVar
  {
    private IJavaValue value;

    /**
     * Depth of this variable in tree. The root node will have depth = 0.
     */
    private int depth;

    public ExpandableVar(IJavaValue value, int depth)
    {
      this.value = value;
      this.depth = depth;
    }
  }


  public List<String> get(IThread thread)
  {
    // Since the view is being reloaded, we can clear existing entries
    expandableVarMap.clear();

    if (thread == null) {
      return null;
    } else {
      List<String> results = new ArrayList<String>();

      // Protect against variable information unavailable for native
      // methods
      try {
        IStackFrame stackFrame = thread.getTopStackFrame();
        if (stackFrame != null) {
          process(thread.getTopStackFrame().getVariables(), results, ROOT_DEPTH);
        }
      } catch (DebugException e) {
        // Suppress exception as it is possible to get an error when the current
        // stack frame points to native method. Variable information is not
        // available in this case.
        if (logger.isDebugEnabled()) {
          logger.debug("Unable to get variables", e);
        }
      }
      return results;
    }
  }

  public List<String> expandValue(long valueId)
  {
    ExpandableVar expandableVar = expandableVarMap.get(valueId);

    if (expandableVar == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("No variable value found with ID: " + valueId);
      }
      return null;
    }

    IJavaValue value = expandableVar.value;
    List<String> results = new ArrayList<String>();
    // Suppress any exception. No point in letting it propagate
    try {
      process(value.getVariables(), results, expandableVar.depth + 1);

      // Remove this value as it cannot be expanded anymore
      expandableVarMap.remove(valueId);
    } catch (DebugException e) {
      logger.error("Unable to get variables", e);
    }
    return results;
  }

  public void removeVariables()
  {
    expandableVarMap.clear();
  }

  /**
   * Process the variables and adds them to the result set.
   * Some variables may be excluded because they are not important for
   * deugging purposes.
   * @see #ignoreVar method.
   *
   * @param vars variables
   * @param results final results containing the variable text
   * @param depth current nesting depth in the tree hierarchy
   */
  private void process(IVariable[] vars, List<String> results, int depth)
    throws DebugException
  {
    if (vars == null) {
      return;
    }

    for (IVariable var : vars) {
      JDIVariable jvar = (JDIVariable) var;
      if (jvar.isSynthetic() ||
          ignoreVar(jvar))
      {
        continue;
      }

      JDIValue value = (JDIValue) var.getValue();
      boolean isLeafNode = !((value != null) &&
        (value instanceof IJavaObject) && 
        value.hasVariables());

      // Treat String as leaf node even though it has child variables
      isLeafNode = isLeafNode || ViewUtils.isStringValue(value);

      String prefix = getIndentation(depth, isLeafNode);
      results.add(prefix + getVariableText(jvar));

      // Keep track of this value as it is shown in UI and could be expanded
      if (!isLeafNode) {
        expandableVarMap.put(((IJavaObject) value).getUniqueId(),
            new ExpandableVar(value, depth));

        // Hack: Add an empty line so that VIM will think there are child nodes
        // and fold correctly.
        String childPrefix = getIndentation(depth + 1, true);
        results.add(childPrefix);
      }
    }
  }

  /**
   * Igmores final primitive variables.
   */
  private boolean ignoreVar(JDIVariable var)
    throws DebugException
  {
    if (var.isFinal()) {
      JDIValue value = (JDIValue) var.getValue();
      if (value instanceof IJavaObject) {
        return false;
      } else {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines whether nested variables should be returned as part of result
   * set. Most times, we don't want to return fields that are part of Class
   * that are not part of the source code. For e.g., java.lang.String contains
   * fields that the application debugger doesn't care about. We only need to
   * return the value of the string in this case.
   *
   */
  private boolean includeNestedVar(JDIValue value)
    throws DebugException
  {
    return true;
    /*
    boolean nesting = true;

    if ((value instanceof IJavaArray) ||
        (value instanceof IJavaClassObject))
    {

      nesting = false;
    } else {
      JDIType type = (JDIType) value.getJavaType();
      // TODO Add tools.jar to access com.sun classes
      //type.getUnderlyingType();

      if (type == null) {
        nesting = false;
      } else {
        String typeName = type.getName();

        // TODO Instead of listing what to ignore, find them out by looking at
        // the source locator for class names that are not present.
        if (typeName.equals("java.util.List") ||
            typeName.equals("java.util.Map") ||
            typeName.equals("java.lang.String"))
        {
          nesting = false;
        }
      }
    }

    return nesting;
    */
  }

  /**
   * Returns the prefix string to use to simulate indentation.
   */
  private String getIndentation(int level, boolean isLeafNode)
  {
    if (level == ROOT_DEPTH) {
      if (isLeafNode) {
        return ViewUtils.LEAF_NODE_SYMBOL;
      } else {
        return ViewUtils.EXPANDED_NODE_SYMBOL;
      }
    }

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < level - 1; i++) {
      sb.append(ViewUtils.LEAF_NODE_INDENT);
    }

    // Special indent for last level since the tree symbol is involved
    if (isLeafNode) {
      sb.append(ViewUtils.LEAF_NODE_INDENT +
          ViewUtils.LEAF_NODE_SYMBOL);
    } else {
      sb.append(ViewUtils.NON_LEAF_NODE_INDENT +
          ViewUtils.EXPANDED_NODE_SYMBOL);
    }

    return sb.toString();
  }

  private String getVariableText(IJavaVariable var)
  {
    String varLabel = ViewUtils.UNKNOWN;
    try {
      varLabel = var.getName();
    } catch (DebugException exception) {}

    IJavaValue javaValue = null;
    try {
      javaValue = (IJavaValue) var.getValue();
    } catch (DebugException e1) {}

    StringBuilder buff = new StringBuilder();
    buff.append(varLabel);

    // Add declaring type name if required
    if (var instanceof IJavaFieldVariable) {
      IJavaFieldVariable field = (IJavaFieldVariable) var;
      if (isDuplicateName(field)) {
        try {
          String decl = field.getDeclaringType().getName();
          buff.append(NLS.bind(" ({0})",
                new String[]{ViewUtils.getQualifiedName(decl)}));
        } catch (DebugException e) {}
      }
    }

    String valueString = getFormattedValueText(javaValue);

    // Do not put the equal sign for array partitions
    if (valueString.length() != 0) {
      buff.append(" = ");
      buff.append(valueString);
    }
    return buff.toString();
  }

  /**
   * Returns whether the given field variable has the same name as any variables
   */
  private boolean isDuplicateName(IJavaFieldVariable variable)
  {
    IJavaReferenceType javaType = variable.getReceivingType();
    try {
      String[] names = javaType.getAllFieldNames();
      boolean found = false;
      for (int i = 0; i < names.length; i++) {
        if (variable.getName().equals(names[i])) {
          if (found) {
            return true;
          }
          found = true;
        }
      }
      return false;
    } catch (DebugException e) {}

    return false;
  }

  /**
   * Returns text for the given value based on user preferences to display
   * toString() details.
   *
   * @param javaValue
   * @return text
   */
  private String getFormattedValueText(IJavaValue javaValue)
  {
    String valueString = ViewUtils.UNKNOWN;
    if (javaValue != null) {
      try {
        valueString = getValueText(javaValue);
      } catch (DebugException exception) {}
    }

    return valueString;
  }

  /**
   * Build the text for an IJavaValue.
   *
   * @param value the value to get the text for
   * @return the value string
   * @throws DebugException if something happens trying to compute the value string
   */
  private String getValueText(IJavaValue value) throws DebugException
  {
    String refTypeName = value.getReferenceTypeName();
    String valueString = value.getValueString();
    boolean isString = ViewUtils.isStringValue(value);
    IJavaType type = value.getJavaType();
    String signature = null;
    if (type != null) {
      signature = type.getSignature();
    }
    if ("V".equals(signature)) {
      valueString = ViewUtils.NO_EXPLICIT_RETURN_VALUE;
    }
    boolean isObject = isObjectValue(signature);
    boolean isArray = value instanceof IJavaArray;
    StringBuilder buffer = new StringBuilder();
    if (isUnknown(signature)) {
      buffer.append(signature);
    } else if (isObject && !isString && (refTypeName.length() > 0)) {
      // Don't show type name for instances and references
      if (!(value instanceof JDIReferenceListValue ||
            value instanceof JDIAllInstancesValue))
      {

        String qualTypeName = ViewUtils.getQualifiedName(refTypeName).trim();
        if (isArray) {
          qualTypeName = adjustTypeNameForArrayIndex(qualTypeName,
              ((IJavaArray)value).getLength());
        }
        buffer.append(qualTypeName);
        buffer.append(' ');
      }
    }

    // Put double quotes around Strings
    if (valueString != null && (isString || valueString.length() > 0)) {
      if (isString) {
        buffer.append('"');
      }
      buffer.append(valueString);
      if (isString) {
        buffer.append('"');
      }

    }

    // show unsigned value second, if applicable
    if (isShowUnsignedValues()) {
      buffer = appendUnsignedText(value, buffer);
    }
    // show hex value third, if applicable
    if (isShowHexValues()) {
      buffer = appendHexText(value, buffer);
    }
    // show byte character value last, if applicable
    if (isShowCharValues()) {
      buffer = appendCharText(value, buffer);
    }
    return buffer.toString().trim();
  }

  private StringBuilder appendUnsignedText(IJavaValue value, StringBuilder buffer)
    throws DebugException
  {
    String unsignedText = getValueUnsignedText(value);
    if (unsignedText != null) {
      buffer.append(" [");
      buffer.append(unsignedText);
      buffer.append("]");
    }
    return buffer;
  }

  private String getValueUnsignedText(IJavaValue value) throws DebugException
  {
    String sig = getPrimitiveValueTypeSignature(value);
    if (sig == null) {
      return null;
    }

    switch (sig.charAt(0)) {
      case 'B' : // byte
        int byteVal;
        try {
          byteVal = Integer.parseInt(value.getValueString());
        } catch (NumberFormatException e) {
          return null;
        }
        if (byteVal < 0) {
          byteVal = byteVal & 0xFF;
          return Integer.toString(byteVal);
        }
      default :
        return null;
    }
  }

  /**
   * Returns the type signature for this value if its type is primitive.
   * For non-primitive types, null is returned.
   */
  private String getPrimitiveValueTypeSignature(IJavaValue value)
    throws DebugException
  {
    IJavaType type = value.getJavaType();
    if (type != null) {
      String sig = type.getSignature();
      if (sig != null && sig.length() == 1) {
        return sig;
      }
    }
    return null;
  }

  /**
   * Given a JNI-style signature String for a IJavaValue, return true
   * if the signature represents an Object or an array of Objects.
   *
   * @param signature the signature to check
   * @return <code>true</code> if the signature represents an object;
   * <code>false</code> otherwise
   */
  private boolean isObjectValue(String signature)
  {
    if (signature == null) {
      return false;
    }
    String type = Signature.getElementType(signature);
    char sigchar = type.charAt(0);
    if(sigchar == Signature.C_UNRESOLVED ||
        sigchar == Signature.C_RESOLVED)
    {
      return true;
    }
    return false;
  }

  /**
   * Returns <code>true</code> if the given signature is not <code>null</code> and
   * matches the text '&lt;unknown&gt;'
   *
   * @param signature the signature to compare
   * @return <code>true</code> if the signature matches '&lt;unknown&gt;'
   */
  boolean isUnknown(String signature)
  {
    if(signature == null) {
      return false;
    }
    return ViewUtils.UNKNOWN.equals(signature);
  }

  /**
   * Given the reference type name of an array type, insert the array length
   * in between the '[]' for the first dimension and return the result.
   */
  private String adjustTypeNameForArrayIndex(String typeName, int arrayIndex)
  {
    int firstBracket = typeName.indexOf("[]");
    if (firstBracket < 0) {
      return typeName;
    }
    StringBuilder buffer = new StringBuilder(typeName);
    buffer.insert(firstBracket + 1, Integer.toString(arrayIndex));
    return buffer.toString();
  }

  private boolean isShowUnsignedValues()
  {
    return false;
  }

  private boolean isShowHexValues()
  {
    return false;
  }

  private boolean isShowCharValues()
  {
    return false;
  }

  private StringBuilder appendHexText(IJavaValue value, StringBuilder buffer)
    throws DebugException
  {
    String hexText = getValueHexText(value);
    if (hexText != null) {
      buffer.append(" [");
      buffer.append(hexText);
      buffer.append("]");
    }
    return buffer;
  }

  private String getValueHexText(IJavaValue value) throws DebugException
  {
    String sig = getPrimitiveValueTypeSignature(value);
    if (sig == null) {
      return null;
    }

    StringBuilder buff = new StringBuilder();
    long longValue;
    char sigValue = sig.charAt(0);
    try {
      if (sigValue == 'C') {
        longValue = value.getValueString().charAt(0);
      } else {
        longValue = Long.parseLong(value.getValueString());
      }
    } catch (NumberFormatException e) {
      return null;
    }
    switch (sigValue) {
      case 'B' :
        buff.append("0x");
        // keep only the relevant bits for byte
        longValue &= 0xFF;
        buff.append(Long.toHexString(longValue));
        break;
      case 'I' :
        buff.append("0x");
        // keep only the relevant bits for integer
        longValue &= 0xFFFFFFFFl;
        buff.append(Long.toHexString(longValue));
        break;
      case 'S' :
        buff.append("0x");
        // keep only the relevant bits for short
        longValue = longValue & 0xFFFF;
        buff.append(Long.toHexString(longValue));
        break;
      case 'J' :
        buff.append("0x");
        buff.append(Long.toHexString(longValue));
        break;
      case 'C' :
        buff.append("\\u");
        String hexString = Long.toHexString(longValue);
        int length = hexString.length();
        while (length < 4) {
          buff.append('0');
          length++;
        }
        buff.append(hexString);
        break;
      default:
        return null;
    }
    return buff.toString();
  }

  private StringBuilder appendCharText(IJavaValue value, StringBuilder buffer)
    throws DebugException
  {
    String charText = getValueCharText(value);
    if (charText != null) {
      buffer.append(" [");
      buffer.append(charText);
      buffer.append("]");
    }
    return buffer;
  }

  /**
   * Returns the character string of a byte or <code>null</code> if
   * the value can not be interpreted as a valid character.
   */
  private String getValueCharText(IJavaValue value) throws DebugException
  {
    String sig = getPrimitiveValueTypeSignature(value);
    if (sig == null) {
      return null;
    }
    String valueString = value.getValueString();
    long longValue;
    try {
      longValue = Long.parseLong(valueString);
    } catch (NumberFormatException e) {
      return null;
    }
    switch (sig.charAt(0)) {
      case 'B' : // byte
        longValue = longValue & 0xFF; // Only lower 8 bits
        break;
      case 'I' : // integer
        longValue = longValue & 0xFFFFFFFF; // Only lower 32 bits
        if (longValue > 0xFFFF || longValue < 0) {
          return null;
        }
        break;
      case 'S' : // short
        longValue = longValue & 0xFFFF; // Only lower 16 bits
        break;
      case 'J' :
        if (longValue > 0xFFFF || longValue < 0) {
          // Out of character range
          return null;
        }
        break;
      default :
        return null;
    }
    char charValue = (char)longValue;
    StringBuilder charText = new StringBuilder();
    if (Character.getType(charValue) == Character.CONTROL) {
      Character ctrl = new Character((char) (charValue + 64));
      charText.append('^');
      charText.append(ctrl);
      switch (charValue) { // common use
        case 0: charText.append(" (NUL)"); break;
        case 8: charText.append(" (BS)"); break;
        case 9: charText.append(" (TAB)"); break;
        case 10: charText.append(" (LF)"); break;
        case 13: charText.append(" (CR)"); break;
        case 21: charText.append(" (NL)"); break;
        case 27: charText.append(" (ESC)"); break;
        case 127: charText.append(" (DEL)"); break;
      }
    } else {
      charText.append(new Character(charValue));
    }
    return charText.toString();
  }
}
