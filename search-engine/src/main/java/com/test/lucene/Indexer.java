package com.test.lucene;

import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.TypeDeclaration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;

import com.test.lucene.analysis.JavaAnalyzer;

public class Indexer {

	private static final Log logger = LogFactory.getLog(Indexer.class);

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			throw new Exception("Usage: java " + Indexer.class.getName()
					+ " <index dir> <data dir>");
		}
		final File indexDir = new File(args[0]);
		final File dataDir = new File(args[1]);

		final long start = System.currentTimeMillis();
		final int numIndexed = index(indexDir, dataDir);
		final long end = System.currentTimeMillis();

		System.out.println("Indexing " + numIndexed + " files took "
				+ (end - start) + " milliseconds");
	}

	public static int index(File indexDir, File dataDir) throws IOException,
			ParseException {
		if (!dataDir.exists() || !dataDir.isDirectory()) {
			throw new IOException(dataDir
					+ " dose not exist or is not a directoty");
		}

		final IndexWriter writer = new IndexWriter(indexDir,
				new JavaAnalyzer(), true);
		writer.setUseCompoundFile(false);

		indexDirectory(writer, dataDir);

		final int numIndexed = writer.docCount();
		writer.optimize();
		writer.close();
		return numIndexed;
	}

	private static void indexDirectory(IndexWriter writer, File dir)
			throws IOException, ParseException {
		final File[] files = dir.listFiles();

		for (final File f : files) {
			if (f.isDirectory()) {
				indexDirectory(writer, f);
			} else if (f.getName().endsWith("sources.jar")) {
				indexJarFile(writer, f);
			}
		}
	}

	private static void indexJarFile(IndexWriter writer, File f)
			throws IOException, ParseException {
		if (f.isHidden() || !f.exists() || !f.canRead()) {
			return;
		}

		logger.info("Indexing " + f.getCanonicalPath());

		final JarFile jarFile = new JarFile(f);

		final Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements()) {
			final JarEntry jarEntry = entries.nextElement();
			if (jarEntry.getName().endsWith(".java")) {
				try {
					index(writer, f, jarFile, jarEntry);
				} catch (Throwable e) {
					logger.error("indexing " + jarEntry.getName() + " error...");
					logger.error(e.getMessage(), e);
					continue;
				}
			}
		}
		jarFile.close();
	}

	private static void index(IndexWriter writer, File f,
			final JarFile jarFile, final JarEntry jarEntry) throws IOException,
			ParseException, CorruptIndexException {
		logger.info("Indexing [" + jarEntry.getName() + "]");
		final Document doc = new Document();
		final InputStream inputStream = jarFile.getInputStream(jarEntry);
		final InputStreamReader reader = new InputStreamReader(
				jarFile.getInputStream(jarEntry));
		final CompilationUnit compilationUnit = JavaParser.parse(inputStream);
		final List<TypeDeclaration> types = compilationUnit.getTypes();
		if (types != null) {
			for (final TypeDeclaration typeDeclaration : types) {
				if (typeDeclaration.getName() != null) {
					doc.add(new Field("type", typeDeclaration.getName(),
							Field.Store.YES, Field.Index.UN_TOKENIZED));
				}
			}
		}
		doc.add(new Field("url", "file:///"
				+ f.getCanonicalPath().replace("\\", "/"), Field.Store.YES,
				Field.Index.UN_TOKENIZED));
		doc.add(new Field("contents", reader));
		doc.add(new Field("jarFile", f.getName(), Field.Store.YES,
				Field.Index.UN_TOKENIZED));
		doc.add(new Field("jarEntry", jarEntry.getName(), Field.Store.YES,
				Field.Index.UN_TOKENIZED));
		writer.addDocument(doc);
	}
}