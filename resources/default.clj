;Here we define a configuration as a Clojure map, it must be at the end of the file to be used by Shuppet.
{
;IAM role doc http://docs.aws.amazon.com/IAM/latest/APIReference/Welcome.html
 :Role {:RoleName $app-name
        ; This tool http://awspolicygen.s3.amazonaws.com/policygen.html helps to create policy documents
        }
 }
