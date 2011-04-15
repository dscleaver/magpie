package com.stuffwithstuff.magpie;

import com.stuffwithstuff.magpie.ast.Expr;
import com.stuffwithstuff.magpie.interpreter.ErrorException;
import com.stuffwithstuff.magpie.interpreter.Interpreter;
import com.stuffwithstuff.magpie.interpreter.InterpreterHost;
import com.stuffwithstuff.magpie.interpreter.Module;
import com.stuffwithstuff.magpie.interpreter.ModuleInfo;
import com.stuffwithstuff.magpie.interpreter.Obj;
import com.stuffwithstuff.magpie.interpreter.QuitException;
import com.stuffwithstuff.magpie.parser.CharacterReader;
import com.stuffwithstuff.magpie.parser.Lexer;
import com.stuffwithstuff.magpie.parser.MagpieParser;
import com.stuffwithstuff.magpie.parser.ParseException;
import com.stuffwithstuff.magpie.parser.TokenType;

public class Repl implements InterpreterHost {
  public void run() {
    System.out.println();
    System.out.println("      _/Oo>");
    System.out.println("     /__/     magpie v0.0.0");
    System.out.println("____//hh___________________");
    System.out.println("   //");
    System.out.println();
    System.out.println("Type 'quit()' and press <Enter> to exit.");
    
    try {
      Interpreter interpreter = new Interpreter(this);
      
      while (true) {
        Lexer lexer = new Lexer("REPL", createReader());
        MagpieParser parser = interpreter.createParser(lexer);
        
        try {
          Expr expr = parser.parseExpression();
          parser.consume(TokenType.LINE);
          
          Obj result = interpreter.interpret(expr);
          String text = interpreter.evaluateToString(result);
          printResult(text);
        } catch(ParseException ex) {
          printError("Parse error: " + ex.getMessage());
        } catch(ErrorException ex) {
          printError(String.format("Uncaught %s: %s",
              ex.getError().getClassObj().getName(), ex.getError().getValue()));
        }
      }
    } catch (QuitException e) {
      // Do nothing.
    }
  }

  @Override
  public boolean allowTopLevelRedefinition() {
    return true;
  }

  @Override
  public void print(String text) {
    System.out.print(text);
  }

  @Override
  public ModuleInfo loadModule(Module loadingModule, String name) {
    return Script.loadModule(loadingModule, name);
  }

  protected CharacterReader createReader() {
    return new ReplCharacterReader();
  }
  
  protected void printResult(String result) {
    System.out.print("= ");
    System.out.println(result);
  }
  
  protected void printError(String message) {
    System.out.println("! " + message);
  }
}
