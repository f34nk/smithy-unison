# S3 Demo Application

Simple test to verify UCM transcript works:

```ucm
.> builtins.merge
```

```unison
helloWorld : '{IO} ()
helloWorld = do
  printLine "Hello from Unison!"
  printLine "UCM transcript is working!"
```

```ucm
.> add
.> run helloWorld
```
