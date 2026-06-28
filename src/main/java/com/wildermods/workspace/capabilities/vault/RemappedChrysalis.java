package com.wildermods.workspace.capabilities.vault;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import com.wildermods.masshash.Hash;
import com.wildermods.thrixlvault.Chrysalis;

public class RemappedChrysalis extends Chrysalis {
	
	public RemappedChrysalis(Chrysalis original, Collection<Function<Path, Path>> rules) {
		super();
		Chrysalis clone = original.clone(); 
		SetMultimap<Hash, Path> blobs = clone.blobs(); 
		LinkedHashMap<Hash, Set<Path>> entries = new LinkedHashMap<>();
		for(Hash hash : blobs.keySet()) {
			Set<Path> paths = blobs.get(hash);
			LinkedHashSet<Path> newPaths = new LinkedHashSet<>();
			for(Path p : paths) {
				for(Function<Path, Path> rule : rules) {
					p = rule.apply(p);
				}
				newPaths.add(p);
			}
			entries.put(hash, newPaths);
		}
		this.blobs = Multimaps.synchronizedSetMultimap(TreeMultimap.create(
			Comparator.comparing(Hash::hash),
			Ordering.natural()
		));
	}

}
