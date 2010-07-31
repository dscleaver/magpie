package com.stuffwithstuff.magpie.interpreter;

public class Obj {
  public Obj() {
    mParent = null;
    mPrimitiveValue = null;
  }
  
  protected Obj(Obj parent) {
    mParent = parent;
    mPrimitiveValue = null;
  }
  
  protected Obj(Obj parent, Object primitiveValue) {
    mParent = parent;
    mPrimitiveValue = primitiveValue;
  }
  
  /**
   * Creates a new Obj with this one as its parent.
   */
  public Obj spawn() { return new Obj(this); }
  
  /**
   * Creates a new Obj with this one as its parent and with the given primitive
   * value.
   */
  public Obj spawn(Object primitiveValue) { return new Obj(this, primitiveValue); }
  
  public Obj getParent() { return mParent; }
  public Object getPrimitiveValue() { return mPrimitiveValue; }
  
  /**
   * Adds the given member to the Obj.
   * @param name   The name of the member.
   * @param member The member's value.
   */
  public void add(String name, Obj member) {
    mScope.define(name, member);
  }
  
  public Obj getMember(String name) {
    // Walk up the parent chain.
    Obj obj = this;
    while (obj != null) {
      Obj member = obj.mScope.get(name);
      if (member != null) return member;
      obj = obj.mParent;
    }
    
    // If we got here, it wasn't found.
    return null;
  }
  
  /**
   * Creates a new EvalContext for evaluating code within the scope of this
   * object's members.
   * @param thisRef The object to bind to "this".
   */
  public EvalContext createContext(Obj thisRef) {
    return new EvalContext(mScope, thisRef);
  }
  
  @Override
  public String toString() {
    // Use the object's name if it has one.
    Obj name = mScope.get("name");
    if (name != null) return name.getPrimitiveValue().toString();
    
    // Else try its value.
    if (mPrimitiveValue == null) return "()";
    return mPrimitiveValue.toString();
  }
  
  private final Obj mParent;
  private final Object mPrimitiveValue;
  private final Scope mScope = new Scope();
}
