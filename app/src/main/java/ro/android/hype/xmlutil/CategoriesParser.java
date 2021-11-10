package ro.android.hype.xmlutil;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class CategoriesParser {

    private static final String ns = null;

    public List<Category> parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readFeed(parser);
        } finally {
            in.close();
        }
    }


    private List<Category> readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        List<Category> categories = new ArrayList<>();

        parser.require(XmlPullParser.START_TAG, ns, "categories");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("category")) {
                Category category = new Category();
                category.setId(Integer.parseInt(parser.getAttributeValue(null, "id")));
                category.setName(readContent(parser, "category"));
                categories.add(category);
            } else {
                skip(parser);
            }
        }
        return categories;
    }

    private String readContent(XmlPullParser parser, String elementName) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, elementName);
        String content = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, elementName);
        return content;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }


    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

}
