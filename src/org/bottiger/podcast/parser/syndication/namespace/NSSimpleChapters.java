package org.bottiger.podcast.parser.syndication.namespace;

import java.util.ArrayList;

import org.bottiger.podcast.parser.syndication.handler.HandlerState;
import org.bottiger.podcast.parser.syndication.util.SyndDateUtils;
import org.xml.sax.Attributes;


public class NSSimpleChapters extends Namespace {
	public static final String NSTAG = "psc|sc";
	public static final String NSURI = "http://podlove.org/simple-chapters";

	public static final String CHAPTERS = "chapters";
	public static final String CHAPTER = "chapter";
	public static final String START = "start";
	public static final String TITLE = "title";
	public static final String HREF = "href";

	@Override
	public SyndElement handleElementStart(String localName, HandlerState state,
			Attributes attributes) {
		/*
		if (localName.equals(CHAPTERS)) {
			state.getCurrentItem().setChapters(new ArrayList<Chapter>());
		} else if (localName.equals(CHAPTER)) {
			state.getCurrentItem()
					.getChapters()
					.add(new SimpleChapter(SyndDateUtils
							.parseTimeString(attributes.getValue(START)),
							attributes.getValue(TITLE), state.getCurrentItem(),
							attributes.getValue(HREF)));
		}
		*/

		return new SyndElement(localName, this);
	}

	@Override
	public void handleElementEnd(String localName, HandlerState state) {
	}

}
