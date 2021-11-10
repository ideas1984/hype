package ro.android.hype.xmlutil;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.List;
import java.util.Map;


public class GamesParser {

    private static final String ns = null;

    public Map<Integer, List<Game>> parse(InputStream in) throws XmlPullParserException, IOException {
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


    private Map<Integer, List<Game>> readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        Map<Integer, List<Game>> gamesMap = new HashMap<>();

        parser.require(XmlPullParser.START_TAG, ns, "games");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("game")) {
                Game game = readGame(parser);

                List<Game> gamesList = gamesMap.get(game.getCategory());
                if(gamesList == null) {
                    gamesList = new ArrayList<>();
                    gamesMap.put(game.getCategory(), gamesList);
                }
                gamesList.add(game);

            } else {
                skip(parser);
            }
        }
        return gamesMap;
    }

    private Game readGame(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "game");

        Game game = new Game();
        game.setId(parser.getAttributeValue(null, "id"));

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("name")) {
                game.setName(readContent(parser, "name"));
            } else if (name.equals("category")) {
                game.setCategory(Integer.parseInt(readContent(parser, "category")));
            }else {
                skip(parser);
            }
        }
        return game;
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
