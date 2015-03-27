/*
 * Copyright 2011-2015 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */

package com.unboundid.scim2.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.unboundid.scim2.Path;
import com.unboundid.scim2.exceptions.SCIMException;
import com.unboundid.scim2.filters.Filter;
import com.unboundid.scim2.filters.FilterType;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.Stack;



/**
 * A parser for SCIM filter expressions.
 */
public class Parser
{

  private static class StringReader extends Reader
  {
    private final String string;
    private int pos;
    private int mark;

    private StringReader(String filterString)
    {
      this.string = filterString;
    }

    @Override
    public int read()
    {
      if(pos >= string.length())
      {
        return -1;
      }
      return string.charAt(pos++);
    }

    public void unread()
    {
      pos--;
    }

    @Override
    public boolean ready()
    {
      return true;
    }

    @Override
    public boolean markSupported()
    {
      return true;
    }

    @Override
    public void mark(int readAheadLimit)
    {
      mark = pos;
    }

    @Override
    public void reset()
    {
      pos = mark;
    }

    @Override
    public long skip(long n)
    {
      long chars = Math.min(string.length() - pos, n);
      pos += chars;
      return chars;
    }

    @Override
    public int read(char[] cbuf, int off, int len)
    {
      if(pos  >= string.length())
      {
        return -1;
      }
      int chars = Math.min(string.length() - pos, len);
      System.arraycopy(string.toCharArray(), pos, cbuf, off, chars);
      pos += chars;
      return chars;
    }

    @Override
    public void close()
    {
      // do nothing.
    }
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Parse the filter provided in the constructor.
   *
   * @return  A parsed SCIM filter.
   *
   * @throws  SCIMException  If the filter string could not be parsed.
   */
  public static Filter parseFilter(final String filterString)
      throws SCIMException
  {
    //  try
    //{
    return readFilter(new StringReader(filterString.trim()), false);
    // }
    //  catch (Exception e)
    //  {
    //   Debug.debugException(e);
    //    throw SCIMException.createException(
    //       400, MessageFormat.format("Invalid filter ''{0}'': {1}",
    //           reader.string, e.getMessage()));
    // }
  }

  public static Path parsePath(final String pathString)
  {
    return readPath(new StringReader(pathString.trim()));
  }

  /**
   * Read a path token. A token is either:
   * <ul>
   *   <li>
   *     An attribute name terminated by a period.
   *   </li>
   *   <li>
   *     An attribute name terminated by an opening brace.
   *   </li>
   *   <li>
   * </ul>
   *
   * @return The token at the current position, or {@code null} if the end of
   *         the input has been reached.
   */
  private static String readPathToken(StringReader reader)
  {
    reader.mark(0);
    int c = reader.read();

    StringBuilder b = new StringBuilder();
    while(c > 0)
    {
      if (c == '.' && !b.toString().endsWith(":"))
      {
        if(reader.pos >= reader.string.length())
        {
          // There is nothing after the period.
          final String msg = String.format(
              "Unexpected end of path string");
          throw new IllegalArgumentException(msg);
        }
        // Terminating period. Consume it and return token.
        return b.toString();
      }
      if (c == '[')
      {
        // Terminating opening brace. Consume it and return token.
        b.append((char)c);
        return b.toString();
      }
      if (c == '-' || c == '_' || c == ':' || Character.isLetterOrDigit(c))
      {
        b.append((char)c);
      }
      else
      {
        final String msg = String.format(
            "Unexpected character '%s' at position %d for token starting at %d",
            (char)c, reader.pos - 1, reader.mark);
        throw new IllegalArgumentException(msg);
      }
      c = reader.read();
    }

    if(b.length() > 0)
    {
      return b.toString();
    }
    return null;
  }

  private static Path readPath(StringReader reader)
  {
    Path path = null;

    String token;

    while ((token = readPathToken(reader)) != null)
    {
      if (token.isEmpty())
      {
        // the only time this is allowed to occur is if the previous attribute
        // had a value filter, in which case, consume the token and move on.
        if(path == null || path.getElements().isEmpty() ||
            path.getElements().get(
                path.getElements().size()-1).getValueFilter() == null)
        {
          final String msg = String.format(
              "Attribute name expected at position %d", reader.mark);
          throw new IllegalArgumentException(msg);
        }
      }
      else
      {
        String schemaUrn = null;
        String attributeName = token;
        Filter valueFilter = null;
        if(path == null &&
            attributeName.toLowerCase().startsWith("urn:"))
        {
          // The attribute name is prefixed with the schema URN.

          // Find the last ":". Everything to the left is the schema URN,
          // everything on the right is the attribute name.
          int i = token.lastIndexOf(':');
          schemaUrn = token.substring(0, i++);
          attributeName = token.substring(i, token.length());
          if(attributeName.isEmpty())
          {
            // The trailing colon signifies that this is an extension root.
            return Path.root(schemaUrn);
          }
        }
        if (attributeName.endsWith("["))
        {
          // There is a value path.
          attributeName =
              attributeName.substring(0, attributeName.length() - 1);
          valueFilter = readFilter(reader, true);
        }
        try
        {
          if (path == null)
          {
            path = Path.fromAttribute(schemaUrn, attributeName, valueFilter);
          }
          else
          {
            path = path.sub(attributeName, valueFilter);
          }
        }
        catch(Exception e)
        {
          Debug.debugException(e);
          final String msg = String.format(
              "Invalid attribute name starting at position %d: %s",
              reader.mark, e.getMessage());
          throw new IllegalArgumentException(msg);
        }
      }
    }

    if(path == null)
    {
      return Path.root();
    }
    return path;
  }

  /**
   * Read a filter token. A token is either:
   * <ul>
   *   <li>
   *     An attribute path terminated by a space or an opening parenthesis.
   *   </li>
   *   <li>
   *     An attribute path terminated by an opening brace.
   *   </li>
   *   <li>
   *     An operator terminated by a space or an opening parenthesis.
   *   </li>
   *   <li>
   *     An opening parenthesis.
   *   </li>
   *   <li>
   *     An closing parenthesis.
   *   </li>
   *   <li>
   *     An closing brace.
   *   </li>
   *   <li>
   *
   *   </li>
   * </ul>
   *
   * @return The token at the current position, or {@code null} if the end of
   *         the input has been reached.
   */
  private static String readFilterToken(StringReader reader, boolean isValueFilter)
  {
    int c;
    do
    {
      // Skip over any leading spaces.
      reader.mark(0);
      c = reader.read();
    }
    while(c == ' ');

    StringBuilder b = new StringBuilder();
    while(c > 0)
    {
      if (c == ' ')
      {
        // Terminating space. Consume it and return token.
        return b.toString();
      }
      if (c == '(' || c == ')')
      {
        if(b.length() > 0)
        {
          // Do not consume the parenthesis.
          reader.unread();
        }
        else
        {
          b.append((char)c);
        }
        return b.toString();
      }
      if (!isValueFilter && c == '[')
      {
        // Terminating opening brace. Consume it and return token.
        b.append((char)c);
        return b.toString();
      }
      if (isValueFilter && c == ']')
      {
        if(b.length() > 0)
        {
          // Do not consume the closing brace.
          reader.unread();
        }
        else
        {
          b.append((char)c);
        }
        return b.toString();
      }
      if (c == '-' || c == '_' || c == '.' || c == ':' ||
          Character.isLetterOrDigit(c))
      {
        b.append((char)c);
      }
      else
      {
        final String msg = String.format(
            "Unexpected character '%s' at position %d for token starting at %d",
            (char)c, reader.pos - 1, reader.mark);
        throw new IllegalArgumentException(msg);
      }
      c = reader.read();
    }

    if(b.length() > 0)
    {
      return b.toString();
    }
    return null;
  }

  private static Filter readFilter(StringReader reader, boolean isValueFilter)
  {
    final Stack<Filter> outputStack = new Stack<Filter>();
    final Stack<String> precedenceStack = new Stack<String>();

    String token;
    String previousToken = null;

    while((token = readFilterToken(reader, isValueFilter)) != null)
    {
      if(token.equals("(") && expectsNewFilter(previousToken))
      {
        precedenceStack.push(token);
      }
      else if(token.equalsIgnoreCase(FilterType.NOT.getStringValue()) &&
          expectsNewFilter(previousToken))
      {
        // "not" should be followed by an (
        String nextToken = readFilterToken(reader, isValueFilter);
        if(nextToken == null)
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }
        if(!nextToken.equals("("))
        {
          final String msg = String.format(
              "Expected '(' at position %d", reader.mark);
          throw new IllegalArgumentException(msg);
        }
        precedenceStack.push(token);
      }
      else if(token.equals(")") && !expectsNewFilter(previousToken))
      {
        String operator = closeGrouping(precedenceStack, outputStack, false);
        if(operator == null)
        {
          final String msg =
              String.format("No opening parenthesis matching closing " +
                  "parenthesis at position %d", reader.mark);
          throw new IllegalArgumentException(msg);
        }
        if (operator.equalsIgnoreCase(FilterType.NOT.getStringValue()))
        {
          // Treat "not" the same as "(" except wrap everything in a not filter.
          outputStack.push(Filter.not(outputStack.pop()));
        }
      }
      else if(token.equalsIgnoreCase(FilterType.AND.getStringValue()) &&
          !expectsNewFilter(previousToken))
      {
        // and has higher precedence than or.
        precedenceStack.push(token);
      }
      else if(token.equalsIgnoreCase(FilterType.OR.getStringValue()) &&
          !expectsNewFilter(previousToken))
      {
        // pop all the pending ands first before pushing or.
        LinkedList<Filter> andComponents = new LinkedList<Filter>();
        while (!precedenceStack.isEmpty())
        {
          if (precedenceStack.peek().equalsIgnoreCase(FilterType.AND.getStringValue()))
          {
            precedenceStack.pop();
            andComponents.addFirst(outputStack.pop());
          }
          else
          {
            break;
          }
          if(!andComponents.isEmpty())
          {
            andComponents.addFirst(outputStack.pop());
            outputStack.push(Filter.and(andComponents));
          }
        }

        precedenceStack.push(token);
      }
      else if(token.endsWith("[") && expectsNewFilter(previousToken))
      {
        // This is a complex value filter.
        final Path filterAttribute;
        try
        {
          filterAttribute = Path.fromString(
              token.substring(0, token.length() - 1));
        }
        catch (final Exception e)
        {
          Debug.debugException(e);
          final String msg = String.format(
              "Expected an attribute reference at position %d: %s",
              reader.mark, e.getMessage());
          throw new IllegalArgumentException(msg);
        }

        outputStack.push(Filter.hasComplexValue(
            filterAttribute, readFilter(reader, true)));
      }
      else if(isValueFilter && token.equals("]") &&
          !expectsNewFilter(previousToken))
      {
        break;
      }
      else if(expectsNewFilter(previousToken))
      {
        // This must be an attribute path followed by operator and maybe value.
        final Path filterAttribute;
        try
        {
          filterAttribute = Path.fromString(token);
        }
        catch (final Exception e)
        {
          Debug.debugException(e);
          final String msg = String.format(
              "Invalid attribute path at position %d: %s",
              reader.mark, e.getMessage());
          throw new IllegalArgumentException(msg);
        }

        String op = readFilterToken(reader, isValueFilter);

        if(op == null)
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }

        if (op.equalsIgnoreCase(FilterType.PRESENT.getStringValue()))
        {
          outputStack.push(Filter.pr(filterAttribute));
        }
        else
        {
          ValueNode valueNode;
          try
          {
            // Mark the beginning of the JSON value so we can later reset back
            // to this position and skip the actual chars that were consumed
            // by Jackson. The Jackson parser is buffered and reads everything
            // until the end of string.
            reader.mark(0);
            JsonParser parser =
                OBJECT_MAPPER.getJsonFactory().createJsonParser(reader);
            // The object mapper will return a Java null for JSON null.
            // Have to distinguish between reading a JSON null and encountering
            // the end of string.
            if (parser.getCurrentToken() == null && parser.nextToken() == null)
            {
              // End of string.
              valueNode = null;
            }
            else
            {
              valueNode = OBJECT_MAPPER.readValue(parser, ValueNode.class);

              // This is actually a JSON null. Use NullNode.
              if(valueNode == null)
              {
                valueNode = OBJECT_MAPPER.getNodeFactory().nullNode();
              }
            }
            // Reset back to the beginning of the JSON value.
            reader.reset();
            // Skip the number of chars consumed by JSON parser + 1.
            reader.skip(parser.getCurrentLocation().getCharOffset() + 1);
          }
          catch (IOException e)
          {
            final String msg = String.format(
                "Invalid comparison value at position %d: %s",
                reader.mark, e.getMessage());
            throw new IllegalArgumentException(msg);
          }

          if (valueNode == null)
          {
            final String msg = String.format(
                "Unexpected end of filter string");
            throw new IllegalArgumentException(msg);
          }

          if (op.equalsIgnoreCase(FilterType.EQUAL.getStringValue()))
          {
            outputStack.push(Filter.eq(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.NOT_EQUAL.getStringValue()))
          {
            outputStack.push(Filter.ne(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.CONTAINS.getStringValue()))
          {
            outputStack.push(Filter.co(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.STARTS_WITH.getStringValue()))
          {
            outputStack.push(Filter.sw(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.ENDS_WITH.getStringValue()))
          {
            outputStack.push(Filter.ew(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.GREATER_THAN.getStringValue()))
          {
            outputStack.push(Filter.gt(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.GREATER_OR_EQUAL.getStringValue()))
          {
            outputStack.push(Filter.ge(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.LESS_THAN.getStringValue()))
          {
            outputStack.push(Filter.lt(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(FilterType.LESS_OR_EQUAL.getStringValue()))
          {
            outputStack.push(Filter.le(filterAttribute, valueNode));
          } else
          {
            final String msg = String.format(
                "Unrecognized attribute operator '%s' at position %d. " +
                    "Expected: eq,ne,co,sw,ew,pr,gt,ge,lt,le", op, reader.mark);
            throw new IllegalArgumentException(msg);
          }
        }
      }
      else
      {
        final String msg = String.format(
            "Unexpected character '%s' at position %d", token,
            reader.mark);
        throw new IllegalArgumentException(msg);
      }
      previousToken = token;
    }

    closeGrouping(precedenceStack, outputStack, true);

    if(outputStack.isEmpty())
    {
      final String msg = String.format(
          "Unexpected end of filter string");
      throw new IllegalArgumentException(msg);
    }
    return outputStack.pop();
  }

  private static String closeGrouping(Stack<String> operators, Stack<Filter> output,
                                      boolean isAtTheEnd)
  {
    String operator = null;
    String repeatingOperator = null;
    LinkedList<Filter> components = new LinkedList<Filter>();

    // Iterate over the logical operators on the stack until either there are
    // no more operators or an opening parenthesis or not is found.
    while (!operators.isEmpty())
    {
      operator = operators.pop();
      if(operator.equals("(") ||
          operator.equalsIgnoreCase(FilterType.NOT.getStringValue()))
      {
        if(isAtTheEnd)
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }
        break;
      }
      if(repeatingOperator == null)
      {
        repeatingOperator = operator;
      }
      if(!operator.equals(repeatingOperator))
      {
        if(output.isEmpty())
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }
        components.addFirst(output.pop());
        if(repeatingOperator.equalsIgnoreCase(FilterType.AND.getStringValue()))
        {
          output.push(Filter.and(components));
        }
        else
        {
          output.push(Filter.or(components));
        }
        components.clear();
        repeatingOperator = operator;
      }
      if(output.isEmpty())
      {
        final String msg = String.format(
            "Unexpected end of filter string");
        throw new IllegalArgumentException(msg);
      }
      components.addFirst(output.pop());
    }

    if(repeatingOperator != null && !components.isEmpty())
    {
      if(output.isEmpty())
      {
        final String msg = String.format(
            "Unexpected end of filter string");
        throw new IllegalArgumentException(msg);
      }
      components.addFirst(output.pop());
      if(repeatingOperator.equalsIgnoreCase(FilterType.AND.getStringValue()))
      {
        output.push(Filter.and(components));
      }
      else
      {
        output.push(Filter.or(components));
      }
    }

    return operator;
  }

  private static boolean expectsNewFilter(String previousToken)
  {
    return previousToken == null ||
        previousToken.equals("(") ||
        previousToken.equalsIgnoreCase(FilterType.NOT.getStringValue()) ||
        previousToken.equalsIgnoreCase(FilterType.AND.getStringValue()) ||
        previousToken.equalsIgnoreCase(FilterType.OR.getStringValue());
  }
}