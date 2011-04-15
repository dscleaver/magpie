package com.stuffwithstuff.magpie.interpreter;

import java.util.*;
import java.util.Map.Entry;

import com.stuffwithstuff.magpie.ast.*;
import com.stuffwithstuff.magpie.interpreter.builtin.*;
import com.stuffwithstuff.magpie.parser.Grammar;
import com.stuffwithstuff.magpie.parser.Lexer;
import com.stuffwithstuff.magpie.parser.MagpieParser;

public class Interpreter {
  public Interpreter(InterpreterHost host) {
    mHost = host;
    
    mGrammar = new Grammar();

    mGlobals = new Scope(host.allowTopLevelRedefinition());
    
    // TODO(bob): Hack. Push a fake module with . as the path for importing
    // from the REPL.
    mLoadingModules.push(new Module(new ModuleInfo("", ".", null), mGlobals));
    
    EnvironmentBuilder builder = new EnvironmentBuilder(this, mGlobals);
    mClass = builder.createClassClass();
    builder.initialize();
    
    mBoolClass = mGlobals.get("Bool").asClass();
    mFnClass = mGlobals.get("Function").asClass();
    mIntClass = mGlobals.get("Int").asClass();
    mListClass = mGlobals.get("List").asClass();
    mNothingClass = mGlobals.get("Nothing").asClass();
    mRecordClass = mGlobals.get("Record").asClass();
    mStringClass = mGlobals.get("String").asClass();
    mTupleClass = mGlobals.get("Tuple").asClass();
    
    mTrue = instantiate(mBoolClass, true);
    mFalse = instantiate(mBoolClass, false);
    mNothing = instantiate(mNothingClass, null);
    
    // Load the base library and dump it directly into the global scope.
    importModule("base");
    mModules.get("base").importTo(mGlobals);
  }
  
  public void interpret(ModuleInfo info) {
    evaluateModule(new Module(info, mGlobals));
  }

  public Obj interpret(Expr expression) {
    // TODO(bob): Should be current module, not globals.
    return evaluate(expression, new EvalContext(mGlobals));
  }
  
  public Obj evaluate(Expr expr, EvalContext context) {
    ExprEvaluator evaluator = new ExprEvaluator(this);
    return evaluator.evaluate(expr, context);
  }
  
  public String evaluateToString(Obj value) {
    Multimethod multimethod = getCurrentScope().findMultimethod(Name.STRING);
    return multimethod.invoke(this, value).asString();
  }
  
  public boolean objectsEqual(Obj a, Obj b) {
    // Shortcuts to avoid infinite regress. Identical values always match, and
    // "true" and "false" never match each other. This lets us match on values
    // before truthiness or "==" have been bootstrapped.
    if (a == b) return true;

    if (a == mTrue && b == mFalse) return false;
    if (a == mFalse && b == mTrue) return false;
    
    // Recursion base case. If we're in the middle of dispatching a call to
    // "==", don't call it again, just default to identity.
    if (mInObjectsEqual) return a == b;

    Multimethod equals = getCurrentScope().findMultimethod(Name.EQEQ);   
    
    // Bootstrap short-cut. If we haven't defined "==" yet, default to identity.
    if (equals == null) return a == b;
    
    mInObjectsEqual = true;
    Obj result = equals.invoke(this, a, b);
    mInObjectsEqual = false;
    
    return result.asBool();
  }
  
  public Module importModule(String name) {
    // TODO(bob): Check for circular references.
    
    // Figure out which actual module to load so we can determine its full name.
    // TODO(bob): This is kind of gross. The problem is that we don't know if
    // an import contains a relative name or an absolute one. If it's relative,
    // we need to include the loading module's name (i.e. if you load "foo"
    // from "bar", we need "bar.foo" to get an unambiguous name) and we only
    // know that by hitting the filesystem.
    ModuleInfo info = mHost.loadModule(mLoadingModules.peek(), name);
    Module module = mModules.get(info.getName());
    
    // Only load it once.
    if (module == null) {
      module = new Module(info, mGlobals);
      mModules.put(info.getName(), module);
      
      evaluateModule(module);
    }
    
    return module;
  }
  
  public void print(String text) {
    mHost.print(text);
  }
  
  // TODO(bob): Get rid of this once everything is including a friendly message.
  public ErrorException error(String errorClassName) {
    throw error(errorClassName, "");
  }
  
  public ErrorException error(String errorClassName, String message) {
    // Look up the error class.
    ClassObj classObj = mGlobals.get(errorClassName).asClass();

    // TODO(bob): Putting the message in here as the value is kind of hackish,
    // but it ensures we can display an error message even if we aren't able
    // to evaluate any code (like calling "string" on the error).
    Obj error = instantiate(classObj, message);
    
    error.setValue(message);
    
    throw new ErrorException(error);
  }
  
  /**
   * Gets the single value "nothing" of type Nothing.
   * @return
   */
  public Obj nothing() { return mNothing; }

  public ClassObj getClassClass() { return mClass; }
  public ClassObj getBoolClass() { return mBoolClass; }
  public ClassObj getFunctionClass() { return mFnClass; }
  public ClassObj getIntClass() { return mIntClass; }
  public ClassObj getListClass() { return mListClass; }
  public ClassObj getNothingClass() { return mNothingClass; }
  public ClassObj getRecordClass() { return mRecordClass; }
  public ClassObj getStringClass() { return mStringClass; }
  public ClassObj getTupleClass() { return mTupleClass; }

  public Obj createBool(boolean value) {
    return value ? mTrue : mFalse;
  }
  
  public ClassObj createClass(String name, List<ClassObj> parents,
      Map<String, Field> fields, Scope scope) {
    // Create the class.
    ClassObj classObj = new ClassObj(mClass, name, parents, fields, scope);
    
    if (classObj.checkForCollisions()) {
      error("ParentCollisionError");
    }
    
    // Add the constructor.
    Multimethod.define(scope, "init").addMethod(new ClassInit(classObj, scope));
    
    // Add getters and setters for the fields.
    for (Entry<String, Field> entry : fields.entrySet()) {
      // Getter.
      Multimethod getter = Multimethod.define(scope, entry.getKey());
      getter.addMethod(new FieldGetter(classObj, entry.getKey(), scope));

      // Setter, if the field is mutable ("var" instead of "val").
      if (entry.getValue().isMutable()) {
        Multimethod setter = Multimethod.define(scope, entry.getKey() + "_=");
        setter.addMethod(new FieldSetter(classObj,
            entry.getKey(), entry.getValue(), scope));
      }
    }
    
    return classObj;
  }

  public Obj createList(List<Obj> elements) {
    return instantiate(mListClass, elements);
  }

  public Obj createInt(int value) {
    return instantiate(mIntClass, value);
  }

  public Obj createString(String value) {
    return instantiate(mStringClass, value);
  }
  
  public FnObj createFn(FnExpr expr, EvalContext context) {
    return new FnObj(mFnClass, new Function(expr, context.getScope()));
  }
  
  public Obj createTuple(Obj... fields) {
    return createTuple(Arrays.asList(fields));
  }
  
  public Obj createTuple(List<Obj> fields) {
    return instantiate(mTupleClass, fields);
  }
  
  public Obj createRecord(Map<String, Obj> fields) {
    Obj record = instantiate(mRecordClass, null);
    
    for (Entry<String, Obj> field : fields.entrySet()) {
      record.setField(field.getKey(), field.getValue());
    }
    
    return record;
  }
  
  public Obj instantiate(ClassObj classObj, Object primitiveValue) {
    Obj object = new Obj(classObj, primitiveValue);
    
    // Initialize its fields.
    for (Entry<String, Field> field : classObj.getFieldDefinitions().entrySet()) {
      Expr initializer = field.getValue().getInitializer();
      if (initializer != null) {
        Obj value = evaluate(initializer, new EvalContext(classObj.getClosure()));
        object.setField(field.getKey(), value);
      }
    }
    
    return object;
  }
  
  public Scope getCurrentScope() {
    return mLoadingModules.peek().getScope();
  }
  
  public MagpieParser createParser(Lexer lexer) {
    return new MagpieParser(lexer, mGrammar);
  }
  
  public InterpreterHost getHost() {
    return mHost;
  }
  
  public Grammar getGrammar() {
    return mGrammar;
  }
  
  public Obj getConstructingObject() { return mConstructing.peek(); }
  
  public Obj constructNewObject(ClassObj classObj, Obj initArg) {
    Obj newObj = instantiate(classObj, null);
    
    mConstructing.push(newObj);
    // Call the init() multimethod.
    initializeNewObject(classObj, initArg);
    mConstructing.pop();
    
    return newObj;
  }
  
  public void initializeNewObject(ClassObj classObj, Obj arg) {
    Multimethod init = Multimethod.define(classObj.getClosure(), "init");
    // Note: the receiver for init() is the class itself, not the new instance
    // which is considered to be in a hidden state since it isn't initialized
    // yet.
    init.invoke(this, classObj, arg);
  }
  
  private void evaluateModule(Module module) {
    Lexer lexer = new Lexer(module.getPath(), module.getSource());
    MagpieParser parser = createParser(lexer);
    
    mLoadingModules.push(module);
    try {
      // Evaluate every expression in the file. We do this incrementally so
      // that expressions that define parsers can be used to parse the rest of
      // the file.
      while (true) {
        Expr expr = parser.parseTopLevelExpression();
        if (expr == null) break;
        interpret(expr);
      }
    } finally {
      mLoadingModules.pop();
    }
  }
  
  private final InterpreterHost mHost;
  
  private final Map<String, Module> mModules = new HashMap<String, Module>();
  
  private final ClassObj mClass;
  private final ClassObj mBoolClass;
  private final ClassObj mFnClass;
  private final ClassObj mIntClass;
  private final ClassObj mListClass;
  private final ClassObj mNothingClass;
  private final ClassObj mRecordClass;
  private final ClassObj mStringClass;
  private final ClassObj mTupleClass;
  
  private final Obj mNothing;
  private final Obj mTrue;
  private final Obj mFalse;
  
  private final Scope mGlobals;
  private final Stack<Module> mLoadingModules = new Stack<Module>();
  private final Grammar mGrammar;
  
  private final Stack<Obj> mConstructing = new Stack<Obj>();

  private boolean mInObjectsEqual = false;
}
