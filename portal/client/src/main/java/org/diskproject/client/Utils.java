package org.diskproject.client;

import org.diskproject.shared.classes.common.TreeItem;
import com.google.gwt.i18n.client.DateTimeFormat;

import java.util.Comparator;
import java.util.Date;

public class Utils {
	public static Comparator<TreeItem> ascDateOrder = new Comparator<TreeItem>() {
		public int compare (TreeItem l, TreeItem r) {
			String lc = l.getCreationDate();
			String lr = r.getCreationDate();
			if (lc != null && lr != null) {
				DateTimeFormat fm = DateTimeFormat.getFormat("HH:mm:ss yyyy-MM-dd");
				Date dl = fm.parse(lc);
				Date dr = fm.parse(lr);
				if (dl.after(dr)) return -1;
				else return 1;
			} else if (lc != null) {
				return -1;
			} else if (lr != null) {
				return 1;
			}
			return 0;
		}
	};

	public static Comparator<TreeItem> descDateOrder = new Comparator<TreeItem>() {
		public int compare (TreeItem l, TreeItem r) {
			String lc = l.getCreationDate();
			String lr = r.getCreationDate();
			if (lc != null && lr != null) {
				DateTimeFormat fm = DateTimeFormat.getFormat("HH:mm:ss yyyy-MM-dd");
				Date dl = fm.parse(lc);
				Date dr = fm.parse(lr);
				if (dl.before(dr)) return -1;
				else return 1;
			} else if (lc != null) {
				return -1;
			} else if (lr != null) {
				return 1;
			}
			return 0;
		}
	};
	
	public static Comparator<TreeItem> ascAuthorOrder = new Comparator<TreeItem>(){
		public int compare (TreeItem l, TreeItem r) {
			String la = l.getAuthor();
			String ra = r.getAuthor();
			if (la != null && ra != null) {
				return la.compareTo(ra);
			} else if (la != null) {
				return -1;
			} else if (ra != null) {
				return 1;
			}
			return 0;
		}
	};

	public static Comparator<TreeItem> descAuthorOrder = new Comparator<TreeItem>(){
		public int compare (TreeItem l, TreeItem r) {
			String la = l.getAuthor();
			String ra = r.getAuthor();
			if (la != null && ra != null) {
				return ra.compareTo(la);
			} else if (la != null) {
				return -1;
			} else if (ra != null) {
				return 1;
			}
			return 0;
		}
	};
	
	public static String extractPrefix (String URI) {
		return extractPrefix(URI, "#");
	}
	
	public static String extractPrefix (String URI, String prefix) {
		String[] sp = URI.split(prefix);
		if (sp != null && sp.length > 0) {
			return sp[sp.length-1];
		} else {
			return URI;
		}
	}
}