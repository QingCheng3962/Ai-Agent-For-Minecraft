import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class MergeMappings {
	private MergeMappings() {
	}

	public static void main(String[] args) throws IOException {
		// args: <yarnTiny> <mojangProguard> <outA:[official,intermediary]> <outB:[official,mojang]>
		MemoryMappingTree yarn = new MemoryMappingTree();
		MappingReader.read(Path.of(args[0]), MappingFormat.TINY_FILE, yarn);
		System.out.println("yarn    src=" + yarn.getSrcNamespace() + " dst=" + yarn.getDstNamespaces());

		MemoryMappingTree mojang = new MemoryMappingTree();
		MappingReader.read(Path.of(args[1]), MappingFormat.PROGUARD_FILE,
				new MappingNsRenamer(new MappingSourceNsSwitch(mojang, "official"),
						Map.of("source", "mojang", "target", "official")));
		System.out.println("mojang  src=" + mojang.getSrcNamespace() + " dst=" + mojang.getDstNamespaces());
		for (MappingTreeView.ClassMappingView c : mojang.getClasses()) {
			if (c.getSrcName().contains("KeyMapping") || c.getDstName(0).contains("KeyMapping")) {
				System.out.println("    FOUND KeyMapping -> src=" + c.getSrcName() + " dst=" + c.getDstName(0));
				break;
			}
		}

		// File A: official + intermediary (pivot through obfuscated names)
		MemoryMappingTree a = new MemoryMappingTree(yarn);
		a.setSrcNamespace("official");
		a.setDstNamespaces(List.of("intermediary"));
		writeTiny(a, Path.of(args[2]));
		System.out.println("wrote " + args[2]);

		// File B: official + mojang readable
		MemoryMappingTree b = new MemoryMappingTree(mojang);
		normalize(b, "official", "mojang");
		writeTiny(b, Path.of(args[3]));
		System.out.println("wrote " + args[3]);
	}

	private static void normalize(MemoryMappingTree tree, String src, String dst) {
		String curSrc = tree.getSrcNamespace();
		List<String> dsts = tree.getDstNamespaces();
		System.out.println("normalize: curSrc=" + curSrc + " dsts=" + dsts);
		if (curSrc.equals(src)) {
			tree.setDstNamespaces(List.of(dst));
		} else if (dsts.contains(src)) {
			tree.setSrcNamespace(src);
			tree.setDstNamespaces(List.of(dst));
		} else {
			throw new IllegalStateException("cannot normalize to " + src + "/" + dst);
		}
	}

	private static void writeTiny(MemoryMappingTree tree, Path out) throws IOException {
		try (MappingWriter w = MappingWriter.create(out, MappingFormat.TINY_2_FILE)) {
			tree.accept(w, VisitOrder.createByName());
		}
	}
}
