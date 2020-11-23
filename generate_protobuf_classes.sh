#!/bin/bash

protoc -Ijbpm-flow/src/main/resources -I../drools/drools-core/src/main/resources --java_out=jbpm-flow/src/main/java jbpm-flow/src/main/resources/org/jbpm/marshalling/jbpmmessages.proto
