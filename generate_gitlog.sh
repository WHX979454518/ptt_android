#!/bin/sh

git log --pretty=format:"<li><i>%cr</i>: <b>%s</b> <i>%an</i></li> " -n 3 | sed 's/"/&quot;/g' | tr '\n' ' '