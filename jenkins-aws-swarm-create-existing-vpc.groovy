env.KEY_NAME = 'fontanoz2.dev.aws.usc.edu'
env.KEY_FILENAME = 'fontanoz2.dev.aws.usc.edu.pem'
env.AWS_DEFAULT_REGION = 'us-west-2'
env.STACK_NAME = 'Docker-fontanoz2'
env.MANAGER_INSTANCE_TYPE = 't2.small'
env.INSTANCE_TYPE = 't2.small'
env.MANAGER_SIZE = '3'
env.CLUSTER_SIZE = '1'
env.ENABLE_CLOUDSTOR_EFS = 'yes'

env.VPC_NAME = 'fontanoz-test'
//env.vpcID = 'vpc-cf8985b6'
//env.vpcCIDR = '172.31.0.0/16'
env.VPC_NAME_PUBLIC_SUBNET1 = 'fontanoz-test-subnet1'
env.VPC_NAME_PUBLIC_SUBNET2 = 'fontanoz-test-subnet2'
env.VPC_NAME_PUBLIC_SUBNET3 = 'fontanoz-test-subnet3'
//env.VPC_PUBLIC_SUBNET1 = 'subnet-6375f728'
//env.VPC_PUBLIC_SUBNET2 = 'subnet-751d7d0c'
//env.VPC_PUBLIC_SUBNET3 = 'subnet-3b730a61'
env.TAG_PROJECT = 'aws'
env.TAG_OWNER = 'ems'
env.TAG_CONTACT = 'Jaime Fontanoza'
env.TAG_EMAIL = 'fontanoz@usc.edu'
env.HOSTED_ZONE_NAME = 'fontanoz2.dev.aws.usc.edu'

node {
    
    def managerIP
    def swarmwideSecurityGroupID
    def defaultDNSTarget
    def elbDNSZoneID
	def hostedZoneID
    def vpcID
    def vpcCIDR
    def vpcIDPublicSubnet1
    def vpcIDPublicSubnet2
    def vpcIDPublicSubnet3

    stage("Determine VPC Info") {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: '8ca743fb-f649-4d1a-8eef-be3b62f3a749']]) {        
            def queryVPC = sh returnStdout: true, script: 'aws ec2 describe-vpcs --filter "Name=tag:Name,Values=$VPC_NAME"'
            writeFile file: "vpc.txt", text: queryVPC

            vpcID = sh returnStdout: true, script: 'jq -r ".Vpcs[0].VpcId" vpc.txt'
            vpcID = vpcID.trim()
            echo "vpcID = ${vpcID}"
        
            vpcCIDR = sh returnStdout: true, script: 'jq -r ".Vpcs[0].CidrBlock" vpc.txt'
            vpcCIDR = vpcCIDR.trim()
            echo "vpcCIDR = ${vpcCIDR}"

            echo "vpcID = $vpcID"
            echo "vpcID = ${vpcID}"
            def cmd = "aws ec2 describe-subnets --filter \"Name=vpc-id,Values=$vpcID\""
            def vpcSubnetQuery = sh returnStdout: true, script: cmd
            writeFile file: "vpc-subnets.txt", text: vpcSubnetQuery

            cmd = "jq -r '.Subnets[] | select(.Tags[].Value == \"$VPC_NAME_PUBLIC_SUBNET1\") | .SubnetId' vpc-subnets.txt"
            vpcIDPublicSubnet1 = sh returnStdout: true, script: cmd
            vpcIDPublicSubnet1 = vpcIDPublicSubnet1.trim()
            echo "vpcIDPublicSubnet1 = ${vpcIDPublicSubnet1}"

            cmd = "jq -r '.Subnets[] | select(.Tags[].Value == \"$VPC_NAME_PUBLIC_SUBNET2\") | .SubnetId' vpc-subnets.txt"
            vpcIDPublicSubnet2 = sh returnStdout: true, script: cmd
            vpcIDPublicSubnet2 = vpcIDPublicSubnet2.trim()
            echo "vpcIDPublicSubnet2 = ${vpcIDPublicSubnet2}"

            cmd = "jq -r '.Subnets[] | select(.Tags[].Value == \"$VPC_NAME_PUBLIC_SUBNET3\") | .SubnetId' vpc-subnets.txt"
            vpcIDPublicSubnet3 = sh returnStdout: true, script: cmd
            vpcIDPublicSubnet3 = vpcIDPublicSubnet3.trim()
            echo "vpcIDPublicSubnet3 = ${vpcIDPublicSubnet3}"
        } // withCredentials
        
//        currentBuild.result = 'ABORTED'
//        error('Stopping early')
    
    } // stage("Determine VPC Info")

    stage("Recreate Key") {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: '8ca743fb-f649-4d1a-8eef-be3b62f3a749']]) {
            def resultDeleteKey = sh returnStdout: true, script: 'aws ec2 delete-key-pair --key-name ${KEY_NAME} || true'
            echo "resultDeleteKey = ${resultDeleteKey}"

            def resultCreateKey = sh returnStdout: true, script: 'aws ec2 create-key-pair --key-name ${KEY_NAME}'
            echo "resultCreateKey = ${resultCreateKey}"
            writeFile file: "${KEY_NAME}-material.pem", text: resultCreateKey

            def keyMaterial = sh returnStdout: true, script: 'jq -r \'.KeyMaterial\' ${KEY_NAME}-material.pem'
            echo "keyMaterial = ${keyMaterial}"

            writeFile file: "${KEY_FILENAME}", text: keyMaterial

            sh "chmod 600 ${KEY_FILENAME}"

        } // withCredentials
    } // stage("Recreate Key")
   
    stage("Create Stack") {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: '8ca743fb-f649-4d1a-8eef-be3b62f3a749']]) {

            env.vpcID = vpcID
            env.vpcCIDR = vpcCIDR
            env.vpcIDPublicSubnet1 = vpcIDPublicSubnet1
            env.vpcIDPublicSubnet2 = vpcIDPublicSubnet2
            env.vpcIDPublicSubnet3 = vpcIDPublicSubnet3
		
            def resultCreateStack = sh returnStdout: true, script: '''aws cloudformation create-stack \
--template-url https://editions-us-east-1.s3.amazonaws.com/aws/stable/Docker-no-vpc.tmpl \
--stack-name ${STACK_NAME} \
--capabilities CAPABILITY_IAM \
--parameters \
ParameterKey=KeyName,ParameterValue=${KEY_NAME} \
ParameterKey=InstanceType,ParameterValue=${INSTANCE_TYPE}  \
ParameterKey=ManagerInstanceType,ParameterValue=${MANAGER_INSTANCE_TYPE} \
ParameterKey=ManagerSize,ParameterValue=${MANAGER_SIZE} \
ParameterKey=ClusterSize,ParameterValue=${CLUSTER_SIZE} \
ParameterKey=EnableCloudStorEfs,ParameterValue=${ENABLE_CLOUDSTOR_EFS} \
ParameterKey=Vpc,ParameterValue=${vpcID} \
ParameterKey=VpcCidr,ParameterValue=${vpcCIDR} \
ParameterKey=PubSubnetAz1,ParameterValue=${vpcIDPublicSubnet1} \
ParameterKey=PubSubnetAz2,ParameterValue=${vpcIDPublicSubnet2} \
ParameterKey=PubSubnetAz3,ParameterValue=${vpcIDPublicSubnet3} \
--tags \
"Key=Project,Value=${TAG_PROJECT}" \
"Key=Owner,Value=${TAG_OWNER}" \
"Key=Contact,Value=${TAG_CONTACT}" \
"Key=Email,Value=${TAG_EMAIL}"
                '''
            echo "resultCreateStack = ${resultCreateStack}"
                
            def r
            timeout(20) {
                echo "beginning sleepy stack check, interval: 30, max: 1200"
                waitUntil {
                    script {
                        sleep 30
                        def result = sh returnStdout: true, script: 'aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query \'Stacks[0].StackStatus\' --output text'
                        r = result.trim()
                        echo "current status: ${r}"
                        return (r != "CREATE_IN_PROGRESS")
                    }
                }
            } // timeout(20)
                
            echo "end result after timeout: ${r}"
            if(r != "CREATE_COMPLETE") {
                error("Stack creation failed: ${r}")
            }

        } // withCredentials
    }   // Stage("Create Stack")
    
    stage("Collect Stack Info") {
         withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: '8ca743fb-f649-4d1a-8eef-be3b62f3a749']]) {
       
            def queryManager = sh returnStdout: true, script: 'aws ec2 describe-instances --filter "Name=tag:Name,Values=$STACK_NAME-Manager" "Name=instance-state-name,Values=running"'
            writeFile file: "managers.txt", text: queryManager
            managerIP = sh returnStdout: true, script: 'jq -r ".Reservations[0].Instances[0].PublicIpAddress" managers.txt'
            managerIP = managerIP.trim()
            echo "managerIP = ${managerIP}"
        
            swarmwideSecurityGroupID = sh returnStdout: true, script: 'aws cloudformation describe-stacks --stack-name $STACK_NAME --query "Stacks[0].Outputs[?OutputKey==\'SwarmWideSecurityGroupID\'].OutputValue" --output text'
            swarmwideSecurityGroupID = swarmwideSecurityGroupID.trim()
            echo "swarmwideSecurityGroupID = ${swarmwideSecurityGroupID}"

            defaultDNSTarget = sh returnStdout: true, script: 'aws cloudformation describe-stacks --stack-name $STACK_NAME --query "Stacks[0].Outputs[?OutputKey==\'DefaultDNSTarget\'].OutputValue" --output text'
            defaultDNSTarget = defaultDNSTarget.trim()
            echo "defaultDNSTarget = ${defaultDNSTarget}"

            elbDNSZoneID = sh returnStdout: true, script: 'aws cloudformation describe-stacks --stack-name $STACK_NAME --query "Stacks[0].Outputs[?OutputKey==\'ELBDNSZoneID\'].OutputValue" --output text'
            elbDNSZoneID = elbDNSZoneID.trim()
            echo "elbDNSZoneID = ${elbDNSZoneID}"
         }
        
    } // stage("Collect Stack Info")
    
    stage("Create DNS Hosted Zone") {
         withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: '8ca743fb-f649-4d1a-8eef-be3b62f3a749']]) {

			// reusable delegation set id created in advance via "aws route53 create-reusable-delegation-set"
            def reusableDelegationSetID = "NYWWP2553YG74"

            def hostedZoneCallerRef = sh returnStdout: true, script: 'python -c \'import uuid; print(uuid.uuid4())\''
            hostedZoneCallerRef = hostedZoneCallerRef.trim()
            echo "hostedZoneCallerRef = ${hostedZoneCallerRef}"
            def cmd = "aws route53 create-hosted-zone --caller-reference ${hostedZoneCallerRef} --delegation-set-id ${reusableDelegationSetID} --name ${HOSTED_ZONE_NAME}"
            def resultCreateZone = sh returnStdout: true, script: cmd

            def resultListZones = sh returnStdout: true, script: 'aws route53 list-hosted-zones'
            echo "resultListZones = ${resultListZones}"
            writeFile file: "pipe.txt", text: resultListZones

            def hostedZone = sh returnStdout: true, script: 'jq -r ".HostedZones[] | select(.Name == \\"$HOSTED_ZONE_NAME.\\") | .Id" pipe.txt'
            echo "hostedZone = ${hostedZone}"
            writeFile file: "pipe.txt", text: hostedZone
            
            hostedZoneID = sh returnStdout: true, script: 'cut -d\'/\' -f3 pipe.txt'
            hostedZoneID = hostedZoneID.trim()
            echo "hostedZoneID = ${hostedZoneID}"

            def wildcardRecord = "*.${HOSTED_ZONE_NAME}"
            echo "wildcardRecord = ${wildcardRecord}"

            cmd = "aws route53 change-resource-record-sets --hosted-zone-id ${hostedZoneID} --change-batch \'{\"Comment\": \"Update DNS record to the new Loadbalancer Endpoint\", \"Changes\": [{\"Action\": \"UPSERT\",\"ResourceRecordSet\": {\"Type\": \"A\", \"AliasTarget\": { \"EvaluateTargetHealth\": false, \"HostedZoneId\": \"${elbDNSZoneID}\", \"DNSName\": \"${defaultDNSTarget}\"}, \"Name\": \"${wildcardRecord}\"}}]}\'"
            echo "cmd = ${cmd}"

            def resultCreateWildcard = sh returnStdout: true, script: cmd
            resultCreateWildcard = resultCreateWildcard.trim()
            echo "resultCreateWildcard = ${resultCreateWildcard}"

         } // withCredentials
    } // Stage("Create Hosted Zone")
    
    stage("Open Ingress Ports") {
         withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: '8ca743fb-f649-4d1a-8eef-be3b62f3a749']]) {

            def result
            def cmd
            def port

            port = "80"
            cmd = "aws ec2 authorize-security-group-ingress --group-id ${swarmwideSecurityGroupID} --protocol tcp --port ${port} --cidr \"0.0.0.0/0\""        
            echo "${cmd}"
            result = sh returnStdout: true, script: cmd
            echo "${result}"

            port = "443"
            cmd = "aws ec2 authorize-security-group-ingress --group-id ${swarmwideSecurityGroupID} --protocol tcp --port ${port} --cidr \"0.0.0.0/0\""
            echo "${cmd}"
            result = sh returnStdout: true, script: cmd
            echo "${result}"
        } // withCredentials
    } // Stage("Open Ingress Ports")
    
    stage("Create S3 Backup") {
         withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: '8ca743fb-f649-4d1a-8eef-be3b62f3a749']]) {
     
            def result, cmd

            def bucketName ="s3.${HOSTED_ZONE_NAME}"
            def encryptionMethod = "AES256"

            cmd = "aws s3api create-bucket --bucket ${bucketName} --region ${AWS_DEFAULT_REGION} --create-bucket-configuration LocationConstraint=${AWS_DEFAULT_REGION}"
            echo "${cmd}"
            result = sh returnStdout: true, script: cmd
            echo "${result}"

            cmd = "aws s3api put-bucket-versioning --bucket ${bucketName} --versioning-configuration Status=Enabled"
            echo "${cmd}"
            result = sh returnStdout: true, script: cmd
            echo "${result}"

            def jsonInput = '{"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]}'
            echo "${jsonInput}"
            writeFile file: "input.json", text: jsonInput

            cmd = "aws s3api put-bucket-encryption --bucket ${bucketName} --server-side-encryption-configuration file://input.json"
            echo "${cmd}"
            result = sh returnStdout: true, script: cmd
            echo "${result}"

        } // withCredentials
    } // Stage("Create S3 Bucket")      
    
    stage("Run remote docker commands") {
        def r

		def scriptDockerProxyStack = "https://raw.githubusercontent.com/docker-flow/docker-flow-proxy/master/docker-compose-stack.yml"
		
        r = sh returnStdout: true, script: "ssh -i ${KEY_FILENAME} -o StrictHostKeyChecking=no docker@${managerIP} docker node ls"
        echo "${r}"

        r = sh returnStdout: true, script: "ssh -i ${KEY_FILENAME} -o StrictHostKeyChecking=no docker@${managerIP} docker network create --driver overlay proxy"
        echo "${r}"
        
        r = sh returnStdout: true, script: "ssh -i ${KEY_FILENAME} -o StrictHostKeyChecking=no docker@${managerIP} curl -o proxy-stack.yml ${scriptDockerProxyStack}"
        echo "${r}"

        r = sh returnStdout: true, script: "ssh -i ${KEY_FILENAME} -o StrictHostKeyChecking=no docker@${managerIP} docker stack deploy -c proxy-stack.yml proxy"
        echo "${r}"
        
    } // stage("Run docker commands")
    
    stage("Deploy Remote Jenkins") {

        def r
		def scriptJenkinsStack = "https://raw.githubusercontent.com/jfontanoza/uscdev/master/docker-compose-jenkins.yml"
        def remoteJenkinsHostname = "jenkins.${HOSTED_ZONE_NAME}"
        
        echo "remoteJenkinsHostname = ${remoteJenkinsHostname}"

        r = sh returnStdout: true, script: "ssh -i ${KEY_FILENAME} -o StrictHostKeyChecking=no docker@${managerIP} curl -o jenkins.yml ${scriptJenkinsStack}"
        echo "${r}"

        r = sh returnStdout: true, script: "ssh -i ${KEY_FILENAME} -o StrictHostKeyChecking=no docker@${managerIP} sed -i \'s/JENKINS_HOSTNAME/${remoteJenkinsHostname}/g' jenkins.yml"
        echo "${r}"

        r = sh returnStdout: true, script: "ssh -i ${KEY_FILENAME} -o StrictHostKeyChecking=no docker@${managerIP} docker stack deploy -c jenkins.yml jenkins"
        echo "${r}"

    } // stage("deploy remote jenkins")
    
}   // node