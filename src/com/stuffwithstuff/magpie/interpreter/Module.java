package com.stuffwithstuff.magpie.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.stuffwithstuff.magpie.SourceReader;
import com.stuffwithstuff.magpie.SourceFile;
import com.stuffwithstuff.magpie.parser.Grammar;
import com.stuffwithstuff.magpie.parser.InfixParser;
import com.stuffwithstuff.magpie.parser.MagpieParser;
import com.stuffwithstuff.magpie.parser.PrefixParser;
import com.stuffwithstuff.magpie.parser.StringReader;

public class Module {
  public Module(String name, SourceFile info, Interpreter interpreter) {
    mName = name;
    mInfo = info;
    mInterpreter = interpreter;
    mScope = new Scope(this);
  }
  
  public String getName() { return mName; }
  public Interpreter getInterpreter() { return mInterpreter; }
  public Scope getScope() { return mScope; }
  
  private SourceReader readSource() {
    return new StringReader(mInfo.getPath(), mInfo.getSource());
  }
  
  public Map<String, Obj> getExportedVariables() {
    return mExportedVariables;
  }

  public Map<String, Multimethod> getExportedMultimethods() {
    return mExportedMultimethods;
  }
  
  public void importSyntax(Module other) {
    // Import any syntax extensions.
    for (Entry<String, PrefixParser> entry : other.mExportedPrefixParsers.entrySet()) {
      mGrammar.defineParser(entry.getKey(), entry.getValue());
    }

    for (Entry<String, InfixParser> entry : other.mExportedInfixParsers.entrySet()) {
      mGrammar.defineParser(entry.getKey(), entry.getValue());
    }
  }
  
  public void addExport(String name, Obj value) {
    if (mExportedVariables.containsKey(name)) throw new IllegalArgumentException();
    
    mExportedVariables.put(name, value);
  }
  
  public void addExport(String name, Multimethod multimethod) {
    mExportedMultimethods.put(name, multimethod);
  }
  
  public MagpieParser createParser() {
    return MagpieParser.create(readSource(), mGrammar);
  }
  
  public Grammar getGrammar() {
    return mGrammar;
  }

  public void defineSyntax(String keyword, InfixParser parser) {
    mGrammar.defineParser(keyword, parser);
    mExportedInfixParsers.put(keyword, parser);
  }
  
  public void defineSyntax(String keyword, PrefixParser parser) {
    mGrammar.defineParser(keyword, parser);
    mExportedPrefixParsers.put(keyword, parser);
  }
  
  @Override
  public String toString() {
    return mInfo.toString();
  }
  
  private final String mName;
  private final SourceFile mInfo;
  private final Interpreter mInterpreter;
  private final Scope mScope;
  private final Grammar mGrammar = new Grammar();
  private final Map<String, Obj> mExportedVariables =
      new HashMap<String, Obj>();
  private final Map<String, Multimethod> mExportedMultimethods =
      new HashMap<String, Multimethod>();
  private final Map<String, InfixParser> mExportedInfixParsers =
    new HashMap<String, InfixParser>();
  private final Map<String, PrefixParser> mExportedPrefixParsers =
    new HashMap<String, PrefixParser>();
}
