# lambdakube-example

An example of using the [lambda-kube](https://github.com/brosenan/lambda-kube) library.

This project implements
Kubernetes's
[Guestbook Example](https://kubernetes.io/docs/tutorials/stateless-application/guestbook/). 

# Prerequisites
1. `kubectl` needs to be installed and configured to a cluster you have reasonable permissions on.
2. Leiningen (I used 2.7.1, I guess any 2.X should work).

## Usage
From the project directory:
``` $ lein auto run ```

This will start a continous process, inspecting the source code,
running `kubectl apply` on the result every time the source code changes.

Feel free to change the configuration
in [core.clj](src/lambdakube_example/core.clj) and see how it affects
the cluster.

To view the application running, run `kubectl proxy`, and follow 
[this link](http://127.0.0.1:8001/api/v1/namespaces/default/services/frontend:web/proxy/) in a browser.

## Testing
This project uses lambda-kube's [testing framework](https://github.com/brosenan/lambda-kube/blob/master/testing.md) 
for integration tests. To execute them use: ```$ lein test```. The tests themselves can be found [here](https://github.com/brosenan/lambdakube-example/blob/master/test/lambdakube_example/core_test.clj).

## License

Copyright © 2018 Boaz Rosenan

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
