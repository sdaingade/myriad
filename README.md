# Myriad

[![Build Status](https://travis-ci.org/mesos/myriad.svg)](https://travis-ci.org/mesos/myriad)

Myriad is a Mesos framework designed for scaling a YARN cluster on Mesos. Myriad can expand or shrink the resources managed by a YARN cluster in response to events as per configured rules and policies.

The name _Myriad_ means, _countless or extremely great number_. In context of the project, it allows one to expand overall resources managed by Mesos, even when the cluster under Mesos management runs other cluster managers like YARN.

**Please note: Myriad is not yet production ready. However, the project is rapidly progressing with some very useful features.** 

**If you are an admin/developer interested to try out Myriad, please clone the git repo and use [phase1 branch](https://github.com/mesos/myriad/tree/phase1).**

## Getting started

* [How Myriad works](docs/how-it-works.md)
* [Developing Myriad](docs/myriad-dev.md)
* [Local vagrant setup guide](docs/vagrant.md)
* [Myriad REST API](docs/API.md)
* [Myriad Dashboard Development](docs/myriad-dashboard.md)
* [Distribution of Node Manager Binaries](docs/myriad-remote-distribution-configuration.md)
* [Fine Grained Scaling](docs/myriad-fine-grained-scaling.md)
* [Deploying the Myriad Resource-Manger using Docker](docker/README.md)
* [Mesos, YARN and cgroups](docs/cgroups.md)

## Roadmap
Please keep checking this section for updates.

- [x] NodeManager Profiles
- [x] Scale up/down Node Managers via REST API/Web UI
- [x] Framework re-conciliation & HA
- [x] ResourceManager failover/discovery using Marathon/Mesos-DNS
- [x] Fine-grained scaling
- [x] Remote distribution of NodeManager binaries
- [x] Framework checkpointing
- [ ] Launch Job History Server
- [ ] Constraints based Node Manager placement
- [ ] Support multi-tenancy for Node Managers
- [ ] Configuration store for storing rules and policies for clusters managed by Myriad

## Videos and Slides
* MesosCon 2014 - Running YARN alongside Mesos [(video)](https://www.youtube.com/watch?v=d7vZWm_xS9c) [(slides)](https://speakerdeck.com/mohit/running-yarn-alongside-mesos-mesoscon-2014)
* Mesos User Group, March 2015 - Myriad: Integrating Hadoop into the Datacenter [(video)](http://www.youtube.com/watch?v=UMu9n4f62GI)
* MesosCon, Seattle 2015 - Resource Sharing Beyond Boundaries [(video)](https://www.youtube.com/watch?v=lU2VE08fOD4) [(slides)](http://events.linuxfoundation.org/sites/events/files/slides/Apache_Myriad_MesosCon_2015.pdf)
