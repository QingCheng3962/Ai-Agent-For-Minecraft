package com.mcai.agent;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public final class ClipboardUtil implements ClipboardOwner {
	private static final ClipboardOwner OWNER = new ClipboardUtil();

	private ClipboardUtil() {
	}

	public static void copy(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(text), OWNER);
	}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
	}
}
