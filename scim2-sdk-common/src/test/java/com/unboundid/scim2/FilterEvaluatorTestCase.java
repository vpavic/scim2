package com.unboundid.scim2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.ISO8601Utils;
import com.unboundid.scim2.exceptions.SCIMException;
import com.unboundid.scim2.filters.Filter;
import com.unboundid.scim2.filters.FilterEvaluator;
import com.unboundid.scim2.model.BaseScimObject;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;

/**
 * Created by boli on 3/26/15.
 */
public class FilterEvaluatorTestCase
{
  private JsonNode node;
  private Date date;

  @BeforeClass
  public void setup() throws IOException
  {
    date = new Date();

    node = BaseScimObject.createSCIMCompatibleMapper().
        readTree("{\n" +
            "    \"externalId\": \"user:externalId\",\n" +
            "    \"id\": \"user:id\",\n" +
            "    \"meta\": {\n" +
            "        \"created\": \"" + ISO8601Utils.format(date, true) + "\",\n" +
            "        \"lastModified\": \"2015-02-27T11:29:39Z\",\n" +
            "        \"location\": \"http://here/user\",\n" +
            "        \"resourceType\": \"some resource type\",\n" +
            "        \"version\": \"1.0\"\n" +
            "    },\n" +
            "    \"name\": {\n" +
            "        \"first\": \"name:first\",\n" +
            "        \"last\": \"name:last\",\n" +
            "        \"middle\": \"name:middle\"\n" +
            "    },\n" +
            "    \"shoeSize\" : \"12W\",\n" +
            "    \"weight\" : 175.6,\n" +
            "    \"children\" : 5,\n" +
            "    \"true\" : true,\n" +
            "    \"false\" : false,\n" +
            "    \"null\" : null,\n" +
            "    \"empty\" : [],\n" +
            "    \"addresses\": [\n" +
            "      {\n" +
            "        \"type\": \"work\",\n" +
            "        \"streetAddress\": \"100 Universal City Plaza\",\n" +
            "        \"locality\": \"Hollywood\",\n" +
            "        \"region\": \"CA\",\n" +
            "        \"postalCode\": \"91608\",\n" +
            "        \"priority\": 0,\n" +
            "        \"country\": \"USA\",\n" +
            "        \"formatted\": \"100 Universal City Plaza\\nHollywood, CA 91608 USA\",\n" +
            "        \"primary\": true\n" +
            "      },\n" +
            "      {\n" +
            "        \"type\": \"home\",\n" +
            "        \"streetAddress\": \"456 Hollywood Blvd\",\n" +
            "        \"locality\": \"Hollywood\",\n" +
            "        \"region\": \"CA\",\n" +
            "        \"postalCode\": \"91608\",\n" +
            "        \"priority\": 10,\n" +
            "        \"country\": \"USA\",\n" +
            "        \"formatted\": \"456 Hollywood Blvd\\nHollywood, CA 91608 USA\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"password\": \"user:password\",\n" +
            "    \"schemas\": [" +
            "    \"urn:unboundid:schemas:baseSchema\", " +
            "    \"urn:unboundid:schemas:favoriteColor\"" +
            "    ],\n" +
            "    \"urn:unboundid:schemas:favoriteColor\": {\n" +
            "        \"favoriteColor\": \"extension:favoritecolor\"\n" +
            "    },\n" +
            "    \"userName\": \"user:username\"\n" +
            "}");
  }

  /**
   * Retrieves a set of valid filter strings.
   *
   * @return  A set of valid filter strings.
   */
  @DataProvider(name = "testValidFilterStrings")
  public Object[][] getTestValidFilterStrings() throws InterruptedException
  {
    return new Object[][]
        {
            new Object[] { "name.first eq \"nAme:fiRst\"", true },
            new Object[] { "name.first ne \"nAme:fiRst\"", false },
            new Object[] { "null eq null", true },
            new Object[] { "unassigned eq null", true },
            new Object[] { "empty eq null", true },
            new Object[] { "null ne null", false },
            new Object[] { "unassigned ne null", false },
            new Object[] { "empty ne null", false },
            new Object[] { "name.first co \"nAme:fiRst\"", true },
            new Object[] { "name.first sw \"nAme:fiRst\"", true },
            new Object[] { "name.first ew \"nAme:fiRst\"", true },
            new Object[] { "name.first sw \"nAme:\"", true },
            new Object[] { "name.first ew \":fiRst\"", true },
            new Object[] { "weight gt 175.2", true },
            new Object[] { "weight gt 175", true },
            new Object[] { "weight gt 175.6", false },
            new Object[] { "weight ge 175.6", true },
            new Object[] { "weight ge 175", true },
            new Object[] { "weight lt 175.8", true },
            new Object[] { "weight lt 176", true },
            new Object[] { "weight lt 175.6", false },
            new Object[] { "weight le 175.6", true },
            new Object[] { "weight le 176", true },
            new Object[] { "children gt 4.5", true },
            new Object[] { "children gt 4", true },
            new Object[] { "children gt 5", false },
            new Object[] { "children ge 5", true },
            new Object[] { "children ge 4", true },
            new Object[] { "children lt 5.5", true },
            new Object[] { "children lt 6", true },
            new Object[] { "children lt 5", false },
            new Object[] { "children le 5", true },
            new Object[] { "children le 6", true },
            new Object[] { "children pr", true },
            new Object[] { "null pr", false },
            new Object[] { "unassigned pr", false },
            new Object[] { "empty pr", false },
            new Object[] { "true eq true and false eq false", true },
            new Object[] { "true eq true and true eq false", false },
            new Object[] { "true eq true or false eq false", true },
            new Object[] { "true eq true or true eq false", true },
            new Object[] { "not(true eq true)", false },
            new Object[] { "not(true eq false)", true },
            new Object[] { "addresses[type eq \"home\" and streetAddress co \"Hollywood\"]", true },
            new Object[] { "addresses[type eq \"work\" and streetAddress co \"Hollywood\"]", false },
            new Object[] { "addresses.type eq \"work\" and addresses.streetAddress co \"Hollywood\"", true },
            new Object[] { "addresses[priority gt 5 and streetAddress co \"Hollywood\"]", true },
            new Object[] { "addresses.priority ge 10", true },
            new Object[] { "addresses.priority le 0", true },
            new Object[] { "meta.created eq \"" + ISO8601Utils.format(date, true) + "\"", true },
            new Object[] { "meta.created eq \"" + ISO8601Utils.format(date, true,
                TimeZone.getTimeZone("CST")) + "\"", true },
            new Object[] { "meta.created eq \"" + ISO8601Utils.format(date, true,
                TimeZone.getTimeZone("PST")) + "\"", true },
            new Object[] { "meta.created ge \"" +
                ISO8601Utils.format(date, true, TimeZone.getTimeZone("CST")) + "\"", true },
            new Object[] { "meta.created le \"" +
                ISO8601Utils.format(date, true, TimeZone.getTimeZone("CST")) + "\"", true },
            new Object[] { "meta.created gt \"" +
                ISO8601Utils.format(date, true, TimeZone.getTimeZone("CST")) + "\"", false },
            new Object[] { "meta.created lt \"" +
                ISO8601Utils.format(date, true, TimeZone.getTimeZone("CST")) + "\"", false },
            new Object[] { "meta.created gt \"" +
                ISO8601Utils.format(new Date(date.getTime() + 1000), false, TimeZone.getTimeZone("CST")) + "\"", false },
            new Object[] { "meta.created lt \"" +
                ISO8601Utils.format(new Date(date.getTime() + 1000), false, TimeZone.getTimeZone("CST")) + "\"", true },
            new Object[] { "meta.created gt \"" +
                ISO8601Utils.format(new Date(date.getTime() - 1000), false, TimeZone.getTimeZone("CST")) + "\"", true },
            new Object[] { "meta.created lt \"" +
                ISO8601Utils.format(new Date(date.getTime() - 1000), false, TimeZone.getTimeZone("CST")) + "\"", false },
        };
  }

  /**
   * Test that filters matching
   */
  @Test(dataProvider = "testValidFilterStrings")
  public void testBinaryFilterValue(String filter, boolean result)
      throws IOException, SCIMException
  {
    assertEquals(FilterEvaluator.evaluate(Filter.fromString(filter), node),
        result);
  }
}