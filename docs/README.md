# Docs

## Install `nvm`

First [install nvm](https://github.com/nvm-sh/nvm#installing-and-updating) to support local use of different node versions.

## Use Node v12

```
nvm install 12
nvm use 12
```

## Install GitBook CLI
```
nvm use 12
npm install -g gitbook-cli
```

## Build via GitBook CLI
```
gitbook build .
```
```
info: 7 plugins are installed 
info: 6 explicitly listed 
info: loading plugin "highlight"... OK 
info: loading plugin "search"... OK 
info: loading plugin "lunr"... OK 
info: loading plugin "sharing"... OK 
info: loading plugin "fontsettings"... OK 
info: loading plugin "theme-default"... OK 
info: found 3 pages 
info: found 0 asset files 
info: >> generation finished with success in 0.4s ! 
```

## Preview via GitBook CL
```
gitbook serve .
```I
```
Live reload server started on port: 35729
Press CTRL+C to quit ...

info: 7 plugins are installed 
info: loading plugin "livereload"... OK 
info: loading plugin "highlight"... OK 
info: loading plugin "search"... OK 
info: loading plugin "lunr"... OK 
info: loading plugin "sharing"... OK 
info: loading plugin "fontsettings"... OK 
info: loading plugin "theme-default"... OK 
info: found 3 pages 
info: found 0 asset files 
info: >> generation finished with success in 0.4s ! 

Starting server ...
Serving book on http://localhost:4000
```

Browse to `http://localhost:4000` to preview GitBook generated docs.
