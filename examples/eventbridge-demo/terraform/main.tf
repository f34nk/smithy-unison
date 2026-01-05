terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      # https://github.com/hashicorp/terraform-provider-aws/issues/45292
      version = "= 6.22.0"
    }
  }
}

provider "aws" {
  region                      = "us-east-1"
  access_key                  = "dummy"
  secret_key                  = "dummy"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    eventbridge = var.endpoint
  }
}

variable "endpoint" {
  type    = string
  default = "http://localhost:4566"
}

# Create a custom event bus for testing
resource "aws_cloudwatch_event_bus" "demo_bus" {
  name = "unison-demo-bus"
}

# Create a rule that matches all events
resource "aws_cloudwatch_event_rule" "demo_rule" {
  name           = "unison-demo-rule"
  event_bus_name = aws_cloudwatch_event_bus.demo_bus.name
  description    = "Matches all events for demo purposes"
  
  event_pattern = jsonencode({
    source = ["unison.demo"]
  })
}

# Output the event bus name
output "event_bus_name" {
  value = aws_cloudwatch_event_bus.demo_bus.name
}

output "event_bus_arn" {
  value = aws_cloudwatch_event_bus.demo_bus.arn
}
